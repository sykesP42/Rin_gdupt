package top.sykesp42.rin.auto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * UI driver for the auto sign mode. Runs only when the user enables this
 * service in system settings and only executes chains submitted by this
 * app's own executor; it never reacts to events and never logs window
 * content.
 */
class WeChatAutoService : AccessibilityService() {

    private val chainMutex = Mutex()

    /** Set by WaitForWindow; scopes node search to the target app's windows. */
    @Volatile
    private var targetPackage: String? = null

    private var overlay: View? = null

    @Volatile
    private var detached = false

    @Volatile
    private var pasteUsed = false

    private val driver = object : UiDriver, TreeDumper {
        override suspend fun runChain(steps: List<ChainStep>): ChainOutcome =
            this@WeChatAutoService.runChain(steps)

        override fun dumpActive(): String = this@WeChatAutoService.dumpActiveTree()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        detached = false
        AutoRuntime.attach(driver)
    }

    override fun onDestroy() {
        detached = true
        AutoRuntime.detach(driver)
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        detached = true
        AutoRuntime.detach(driver)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private suspend fun runChain(steps: List<ChainStep>): ChainOutcome {
        if (!chainMutex.tryLock()) return ChainOutcome.Busy
        pasteUsed = false
        targetPackage = null
        try {
            val outcome = withTimeoutOrNull(AutoPolicy.CHAIN_TOTAL_TIMEOUT_MS) { execute(steps) }
                ?: ChainOutcome.StepFailed("chain", "TIMEOUT")
            if (pasteUsed) clearClipboard()
            return outcome
        } catch (_: TimeoutCancellationException) {
            if (pasteUsed) clearClipboard()
            return ChainOutcome.StepFailed("chain", "TIMEOUT")
        } catch (_: CancellationException) {
            if (pasteUsed) clearClipboard()
            throw CancellationException("auto chain cancelled")
        } catch (_: Exception) {
            if (pasteUsed) clearClipboard()
            return ChainOutcome.StepFailed("chain", "ERROR")
        } finally {
            chainMutex.unlock()
        }
    }

    private suspend fun execute(steps: List<ChainStep>): ChainOutcome = coroutineScope {
        for (step in steps) {
            if (detached) return@coroutineScope ChainOutcome.Interrupted("SERVICE_DETACHED")
            Log.i(TAG, "step ${step.label}")
            val failure = when (step) {
                is ChainStep.LaunchApp ->
                    if (ensureAppFront(step.packageName)) null else "START_BLOCKED"
                is ChainStep.WaitForWindow -> {
                    targetPackage = step.packageName
                    if (waitActive(step.packageName, step.timeoutMs)) null else "TIMEOUT"
                }
                is ChainStep.EnsureHome ->
                    if (ensureHome(step.matchers, step.maxBacks, step.timeoutMs)) null else "TIMEOUT"
                is ChainStep.EnsureScreen ->
                    if (ensureScreen(step, step.timeoutMs)) null else "TIMEOUT"
                is ChainStep.Click ->
                    if (waitForClick(listOf(step.matcher), step.timeoutMs)) null else "TIMEOUT"
                is ChainStep.ClickAny ->
                    if (waitForClick(step.matchers, step.timeoutMs)) null else "TIMEOUT"
                is ChainStep.InputText ->
                    if (waitForInput(step.matcher, step.text, step.timeoutMs)) null else "TIMEOUT"
                is ChainStep.SearchContact ->
                    if (searchContact(step)) null else "TIMEOUT"
                is ChainStep.Back -> {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(400)
                    null
                }
                is ChainStep.Dismiss -> {
                    dismissIfPresent(step.matchers, step.timeoutMs)
                    null
                }
                is ChainStep.Sleep -> {
                    delay(step.ms)
                    null
                }
            }
            if (failure != null) {
                Log.w(TAG, "step failed ${step.label} reason=$failure")
                return@coroutineScope ChainOutcome.StepFailed(step.label, failure)
            }
        }
        ChainOutcome.Success
    }

    /**
     * Bring the target app to the foreground. A plain startActivity from this
     * bound service is silently BAL-blocked on some OEMs (verified on Honor
     * Android 16), so retry while showing a 1px overlay window: a visible
     * overlay grants the standard background-activity-start exemption.
     */
    private suspend fun ensureAppFront(packageName: String): Boolean {
        runCatching { launchIntent(packageName) }
        if (waitActive(packageName, FIRST_FRONT_TIMEOUT_MS)) return true
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "overlay permission missing; front launch may be blocked")
            return waitActive(packageName, POLL_MS * 5)
        }
        withContext(Dispatchers.Main) { addOverlay() }
        val launched = runCatching { launchIntent(packageName) }.isSuccess
        val active = launched && waitActive(packageName, OVERLAY_FRONT_TIMEOUT_MS)
        withContext(Dispatchers.Main) { removeOverlay() }
        return active
    }

    /** Best-effort dismissal of optional popups; absence is not a failure. */
    private suspend fun dismissIfPresent(matchers: List<NodeSpec>, timeoutMs: Long) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val node = findNode(matchers)
            if (node != null) {
                clickNode(node)
                delay(600)
                return
            }
            delay(POLL_MS)
        }
    }

    /**
     * Reach the goal screen from any start state: goal already visible → done;
     * entry path visible → walk it (e.g. "+" then menu); popup → dismiss;
     * deep page → back. Apps like WeCom resume wherever they were left.
     */
    private suspend fun ensureScreen(step: ChainStep.EnsureScreen, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var backs = 0
        while (SystemClock.elapsedRealtime() < deadline) {
            if (findNode(step.goalMatchers) != null) return true
            val popup = findNode(step.dismissMatchers)
            if (popup != null) {
                clickNode(popup)
                delay(600)
                continue
            }
            var advanced = false
            for (level in step.entryMatchers) {
                val node = findNode(level)
                if (node != null && clickNode(node)) {
                    advanced = true
                    delay(700)
                    break
                }
            }
            if (advanced) continue
            if (backs < step.maxBacks) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                backs++
                delay(800)
            } else {
                delay(POLL_MS)
            }
        }
        return findNode(step.goalMatchers) != null
    }

    private fun launchIntent(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return true
    }

    /** True only when the package owns the active (focused) window. */
    private fun isActiveWindow(packageName: String): Boolean {
        runCatching { rootInActiveWindow }.getOrNull()?.let {
            if (it.packageName == packageName) return true
        }
        runCatching { windows }.getOrDefault(emptyList()).forEach { window ->
            if (window.isActive &&
                runCatching { window.root }.getOrNull()?.packageName == packageName
            ) {
                return true
            }
        }
        return false
    }

    private suspend fun waitActive(packageName: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isActiveWindow(packageName)) return true
            delay(POLL_MS)
        }
        return isActiveWindow(packageName)
    }

    private fun addOverlay() {
        if (overlay != null) return
        val manager = getSystemService(WindowManager::class.java) ?: return
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        overlay = View(this).also { view ->
            runCatching { manager.addView(view, params) }
                .onFailure { Log.w(TAG, "overlay add failed type=${it.javaClass.simpleName}") }
        }
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        overlay = null
        runCatching { getSystemService(WindowManager::class.java)?.removeView(view) }
    }

    private suspend fun ensureHome(matchers: List<NodeSpec>, maxBacks: Int, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var backs = 0
        var relaunched = false
        while (SystemClock.elapsedRealtime() < deadline) {
            if (findNode(matchers) != null) return true
            val target = targetPackage
            val activeIsTarget = target != null && isActiveWindow(target)
            if (!activeIsTarget && !relaunched) {
                // Back (or the system) left WeChat; bring it front again
                // instead of polling a window we can no longer see.
                relaunched = true
                runCatching { ensureAppFront(target!!) }
            } else if (backs < maxBacks) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                backs++
                delay(900)
            } else {
                delay(POLL_MS)
            }
        }
        return findNode(matchers) != null
    }

    private suspend fun waitForClick(matchers: List<NodeSpec>, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val node = findNode(matchers)
            if (node != null && clickNode(node)) {
                delay(350)
                return true
            }
            delay(POLL_MS)
        }
        return false
    }

    private suspend fun waitForInput(matchers: NodeSpec, text: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val node = findNode(listOf(matchers))
            if (node != null && inputText(node, text)) {
                delay(300)
                return true
            }
            delay(POLL_MS)
        }
        return false
    }

    /**
     * Type localized contact-name candidates into the search box until the
     * result row appears, then open the chat. Covers zh/en WeChat UIs.
     */
    private suspend fun searchContact(step: ChainStep.SearchContact): Boolean {
        val deadline = SystemClock.elapsedRealtime() + step.timeoutMs
        for (candidate in step.candidates) {
            if (SystemClock.elapsedRealtime() >= deadline) break
            val input = findNode(listOf(step.inputSpec)) ?: return false
            if (!inputText(input, candidate)) continue
            val candidateDeadline = minOf(
                SystemClock.elapsedRealtime() + step.perCandidateTimeoutMs,
                deadline
            )
            while (SystemClock.elapsedRealtime() < candidateDeadline) {
                val result = findNode(step.resultSpecs)
                if (result != null) {
                    if (clickNode(result)) {
                        delay(600)
                        return true
                    }
                    break
                }
                delay(POLL_MS)
            }
        }
        return false
    }

    private fun windowRoots(): List<AccessibilityNodeInfo> {
        // Once the chain has a target app, ONLY the active window counts.
        // Background windows stay in `windows` for a while and their node
        // bounds would route gesture taps onto whatever is foreground — that
        // is how a stray tap once landed on the launcher's search bar.
        val scoped = targetPackage
        val activeRoot = runCatching { rootInActiveWindow }.getOrNull()
        if (scoped != null) {
            return if (activeRoot?.packageName == scoped) listOf(activeRoot) else emptyList()
        }
        val roots = ArrayList<AccessibilityNodeInfo>(2)
        activeRoot?.let { roots.add(it) }
        runCatching { windows }.getOrDefault(emptyList()).forEach { window ->
            runCatching { window.root }.getOrNull()?.let { root ->
                if (root !in roots) roots.add(root)
            }
        }
        return roots
    }

    private fun findNode(specs: List<NodeSpec>): AccessibilityNodeInfo? {
        val qualified = specs.filter { it.isQualified }
        if (qualified.isEmpty()) return null
        for (root in windowRoots()) {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var visited = 0
            while (queue.isNotEmpty() && visited < MAX_NODES) {
                val node = queue.removeFirst()
                visited++
                if (node.isVisibleToUser && qualified.any { matches(it, node) }) {
                    return node
                }
                for (index in 0 until node.childCount) {
                    runCatching { node.getChild(index) }.getOrNull()?.let { queue.add(it) }
                }
            }
        }
        return null
    }

    private fun matches(spec: NodeSpec, node: AccessibilityNodeInfo): Boolean {
        if (spec.editable != null && node.isEditable != spec.editable) return false
        val className = node.className?.toString().orEmpty()
        if (spec.classNameContains.isNotEmpty() &&
            spec.classNameContains.none { className.contains(it, ignoreCase = true) }
        ) {
            return false
        }
        val viewId = node.viewIdResourceName.orEmpty()
        if (spec.idContains.isNotEmpty() && spec.idContains.none { viewId.contains(it) }) return false
        val text = node.text?.toString().orEmpty()
        if (spec.textEquals.isNotEmpty() && spec.textEquals.none { text == it }) return false
        if (spec.textContains.isNotEmpty() && spec.textContains.none { text.contains(it) }) return false
        val desc = node.contentDescription?.toString().orEmpty()
        if (spec.descEquals.isNotEmpty() && spec.descEquals.none { desc == it }) return false
        if (spec.descContains.isNotEmpty() && spec.descContains.none { desc.contains(it) }) return false
        return spec.isQualified
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < MAX_CLICK_ANCESTORS) {
            if (current.isClickable && runCatching {
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false)
            ) {
                return true
            }
            current = runCatching { current.parent }.getOrNull()
            depth++
        }
        val bounds = Rect()
        runCatching { node.getBoundsInScreen(bounds) }.getOrDefault(Unit)
        if (bounds.isEmpty) return false
        return tap(bounds.exactCenterX(), bounds.exactCenterY())
    }

    private fun inputText(node: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (node.isEditable && runCatching {
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }.getOrDefault(false)
        ) {
            return true
        }
        // Paste fallback: the focused WeChat input reads the clipboard itself;
        // this app only writes it, and the chain clears it when finished.
        runCatching {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(
                ClipData.newPlainText("sign", text)
            )
            pasteUsed = true
        }
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        if (runCatching { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) }.getOrDefault(false)) {
            return true
        }
        clickNode(node)
        return runCatching { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) }.getOrDefault(false)
    }

    private fun tap(x: Float, y: Float): Boolean {
        if (!x.isFinite() || !y.isFinite()) return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = runCatching {
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, GESTURE_MS))
                .build()
        }.getOrNull() ?: return false
        return runCatching { dispatchGesture(gesture, null, null) }.getOrDefault(false)
    }

    private fun clearClipboard() {
        runCatching {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    /** Debug helper: serialize every window root, text truncated to 20 chars. */
    private fun dumpActiveTree(): String {
        val builder = StringBuilder()

        fun boundsShort(node: AccessibilityNodeInfo): String {
            val rect = Rect()
            runCatching { node.getBoundsInScreen(rect) }
            return "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]"
        }

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 25 || builder.length > 60_000) return
            val indent = "  ".repeat(depth)
            val text = node.text?.toString()?.replace('\n', ' ')?.take(20).orEmpty()
            val desc = node.contentDescription?.toString()?.take(20).orEmpty()
            builder.append(indent)
                .append(node.className?.toString()?.substringAfterLast('.') ?: "?")
                .append(" id=").append(node.viewIdResourceName.orEmpty())
                .append(" desc=").append(desc)
                .append(" text=").append(text)
                .append(if (node.isClickable) " click" else "")
                .append(if (node.isEditable) " edit" else "")
                .append(' ')
                .append(boundsShort(node))
                .append('\n')
            for (index in 0 until node.childCount) {
                runCatching { node.getChild(index) }.getOrNull()?.let { walk(it, depth + 1) }
            }
        }

        builder.append("activeRoot pkg=")
            .append(runCatching { rootInActiveWindow?.packageName }.getOrNull() ?: "null")
            .append('\n')
        runCatching { rootInActiveWindow }.getOrNull()?.let { root ->
            builder.append("--- activeRoot subtree ---\n")
            runCatching { walk(root, 1) }
        }
        builder.append("--- window list ---\n")
        val windowList = runCatching { windows }.getOrDefault(emptyList())
        windowList.forEach { window ->
            val root = runCatching { window.root }.getOrNull()
            builder.append("WINDOW pkg=").append(window.runCatching { packageName }.getOrNull())
                .append(" active=").append(window.isActive)
                .append(" focused=").append(window.isFocused)
                .append(" type=").append(window.type)
                .append(" root=").append(if (root == null) "null" else "ok")
                .append('\n')
            root?.let { runCatching { walk(it, 1) } }
        }
        if (windowList.isEmpty()) {
            builder.append("<no windows listed>\n")
            runCatching { rootInActiveWindow }.getOrNull()?.let { runCatching { walk(it, 1) } }
        }
        return builder.toString()
    }

    companion object {
        private const val TAG = "RinAuto"
        private const val POLL_MS = 200L
        private const val MAX_NODES = 800
        private const val MAX_CLICK_ANCESTORS = 6
        private const val GESTURE_MS = 60L
        private const val FIRST_FRONT_TIMEOUT_MS = 2_500L
        private const val OVERLAY_FRONT_TIMEOUT_MS = 3_000L
    }
}
