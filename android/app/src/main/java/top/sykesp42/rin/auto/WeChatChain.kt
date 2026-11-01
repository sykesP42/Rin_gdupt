package top.sykesp42.rin.auto

/**
 * Pure description of the WeChat tap chain. The accessibility service maps
 * [NodeSpec] onto AccessibilityNodeInfo trees; nothing here touches Android,
 * and step labels never carry the sign URL (only [ChainStep.InputText.text]
 * does, and it is excluded from every failure reason).
 *
 * Matching semantics: within one spec, each populated dimension is an OR over
 * its entries, dimensions combine with AND. Specs used together in a
 * [ChainStep.ClickAny] or EnsureHome are alternative strategies.
 */
data class NodeSpec(
    val textEquals: List<String> = emptyList(),
    val textContains: List<String> = emptyList(),
    val descEquals: List<String> = emptyList(),
    val descContains: List<String> = emptyList(),
    val idContains: List<String> = emptyList(),
    val classNameContains: List<String> = emptyList(),
    val editable: Boolean? = null,
    /** Content-free label for failure reasons; required when matchers embed URL fragments. */
    val labelOverride: String? = null
) {
    /** Short, content-free description used in failure reasons and tests. */
    val label: String
        get() = labelOverride ?: buildList {
            if (textEquals.isNotEmpty()) add("text=${textEquals.first()}")
            if (textContains.isNotEmpty()) add("contains=${textContains.first()}")
            if (descEquals.isNotEmpty()) add("desc=${descEquals.first()}")
            if (descContains.isNotEmpty()) add("desc~=${descContains.first()}")
            if (idContains.isNotEmpty()) add("id~=${idContains.first()}")
            if (classNameContains.isNotEmpty()) add("class~=${classNameContains.first()}")
            if (editable == true) add("editable")
        }.joinToString("|").ifEmpty { "any" }

    val isQualified: Boolean
        get() = textEquals.isNotEmpty() || textContains.isNotEmpty() || descEquals.isNotEmpty() ||
            descContains.isNotEmpty() || idContains.isNotEmpty() || classNameContains.isNotEmpty() ||
            editable != null
}

sealed interface ChainStep {
    /** Stable, content-free label for history/failure reasons. */
    val label: String

    data class LaunchApp(val packageName: String) : ChainStep {
        override val label: String get() = "launch"
    }

    data class WaitForWindow(val packageName: String, val timeoutMs: Long) : ChainStep {
        override val label: String get() = "window"
    }

    /** Pop WeChat back to the home tab area before searching. */
    data class EnsureHome(val matchers: List<NodeSpec>, val maxBacks: Int, val timeoutMs: Long) : ChainStep {
        override val label: String get() = "home"
    }

    /**
     * State-tolerant entry into the target UI: succeeds immediately when the
     * goal matchers are visible, otherwise walks the entry matchers (e.g.
     * "+" then menu), dismisses popups and backs out of deep pages.
     */
    data class EnsureScreen(
        val entryMatchers: List<List<NodeSpec>>,
        val goalMatchers: List<NodeSpec>,
        val dismissMatchers: List<NodeSpec>,
        val maxBacks: Int,
        val timeoutMs: Long
    ) : ChainStep {
        override val label: String get() = "enter"
    }

    data class Click(val matcher: NodeSpec, val timeoutMs: Long) : ChainStep {
        override val label: String get() = "click:${matcher.label}"
    }

    data class ClickAny(val matchers: List<NodeSpec>, val timeoutMs: Long) : ChainStep {
        override val label: String get() = "click-any:${matchers.joinToString("/") { it.label }}"
    }

    /**
     * Search a contact by trying localized name candidates one by one:
     * type a candidate, wait for the result row, try the next if absent.
     */
    data class SearchContact(
        val inputSpec: NodeSpec,
        val resultSpecs: List<NodeSpec>,
        val candidates: List<String>,
        val perCandidateTimeoutMs: Long,
        val timeoutMs: Long
    ) : ChainStep {
        override val label: String get() = "search-contact"
    }

    data class InputText(val matcher: NodeSpec, val text: String, val timeoutMs: Long) : ChainStep {
        override val label: String get() = "input:${matcher.label}"
    }

    data object Back : ChainStep {
        override val label: String get() = "back"
    }

    /** Click the matchers if they appear within the window; never fails. */
    data class Dismiss(val matchers: List<NodeSpec>, val timeoutMs: Long) : ChainStep {
        override val label: String get() = "dismiss"
    }

    data class Sleep(val ms: Long) : ChainStep {
        override val label: String get() = "sleep"
    }
}

object WeChatChain {
    const val WECHAT_PACKAGE = "com.tencent.mm"
    const val FILE_TRANSFER_CONTACT = "文件传输助手"
    const val FILE_TRANSFER_CONTACT_EN = "File Transfer"

    /** Home top-bar search entry: description first, resource-id fallback. */
    internal fun homeSearchEntries() = listOf(
        NodeSpec(descEquals = listOf("搜索", "Search")),
        NodeSpec(idContains = listOf("icon_search", "action_search"))
    )

    /** The single editable field on the search page. */
    internal fun searchInput() = NodeSpec(
        editable = true,
        classNameContains = listOf("EditText")
    )

    /** The 文件传输助手 / File Transfer row in the search result list. */
    internal fun fileTransferResult() = listOf(
        NodeSpec(textEquals = listOf(FILE_TRANSFER_CONTACT, FILE_TRANSFER_CONTACT_EN)),
        NodeSpec(descEquals = listOf(FILE_TRANSFER_CONTACT, FILE_TRANSFER_CONTACT_EN))
    )

    /** The single editable chat input on a chat page. */
    internal fun chatInput() = NodeSpec(
        editable = true,
        classNameContains = listOf("EditText")
    )

    /** The send button: text variants first (zh/en), resource-id fallback. */
    internal fun sendButtons() = listOf(
        NodeSpec(textEquals = listOf("发送", "Send")),
        NodeSpec(idContains = listOf("chat_send", "btn_send", "fun_btn"))
    )

    /**
     * The just-sent link bubble. WeChat may ellipsize the visible text, so
     * match stable markers derived from the URL host; never the full URL.
     * School links get their two known markers, any other URL (e.g. the
     * harmless calibration link) matches its host name.
     */
    internal fun linkBubbles(url: String): List<NodeSpec> = when {
        url.contains("class.gdupt.edu.cn") -> listOf(
            NodeSpec(textContains = listOf("signDispactch?data="), labelOverride = "link-bubble"),
            NodeSpec(textContains = listOf("class.gdupt.edu.cn"), labelOverride = "link-bubble")
        )
        else -> listOf(
            NodeSpec(textContains = listOf(hostOf(url)), labelOverride = "link-bubble")
        )
    }

    private fun hostOf(url: String): String {
        val schemeEnd = url.indexOf("//")
        val start = if (schemeEnd >= 0) schemeEnd + 2 else 0
        val pathStart = url.indexOf('/', start)
        val end = if (pathStart > start) pathStart else url.length
        return url.substring(start, end).ifEmpty { url }
    }

    /**
     * 文件传输助手 link path: launch WeChat, search the contact (zh/en UI),
     * send the URL to yourself, then tap the sent bubble so WeChat's built-in
     * browser opens it (silent OAuth does the rest).
     */
    fun weChatLinkChain(url: String): List<ChainStep> = listOf(
        ChainStep.LaunchApp(WECHAT_PACKAGE),
        ChainStep.WaitForWindow(WECHAT_PACKAGE, timeoutMs = 8_000),
        ChainStep.EnsureHome(homeSearchEntries(), maxBacks = 2, timeoutMs = 6_000),
        ChainStep.ClickAny(homeSearchEntries(), timeoutMs = 4_000),
        ChainStep.Click(searchInput(), timeoutMs = 5_000),
        ChainStep.SearchContact(
            inputSpec = searchInput(),
            resultSpecs = fileTransferResult(),
            candidates = listOf(FILE_TRANSFER_CONTACT, FILE_TRANSFER_CONTACT_EN),
            perCandidateTimeoutMs = 3_500,
            timeoutMs = 10_000
        ),
        ChainStep.Click(chatInput(), timeoutMs = 6_000),
        ChainStep.InputText(chatInput(), url, timeoutMs = 5_000),
        ChainStep.Sleep(300),
        ChainStep.ClickAny(sendButtons(), timeoutMs = 4_000),
        ChainStep.Sleep(500),
        ChainStep.ClickAny(linkBubbles(url), timeoutMs = 6_000)
    )
}
