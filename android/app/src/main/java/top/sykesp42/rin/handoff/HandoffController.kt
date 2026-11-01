package top.sykesp42.rin.handoff

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import top.sykesp42.rin.domain.SignUrl

sealed interface HandoffResult {
    data object Opened : HandoffResult
    data object NotInstalled : HandoffResult
    data object Blocked : HandoffResult
    data object InvalidUrl : HandoffResult
}

/**
 * Opens the validated sign URL in WeChat. Never pins a WeChat internal
 * Activity (it changes between versions); the system disambiguates WeChat vs
 * WeChat Favorites. WeCom is only a compatibility fallback.
 */
class HandoffController(private val context: Context) {
    fun openInWeChat(url: String): HandoffResult = open(url, WECHAT_PACKAGE)

    fun openInWeCom(url: String): HandoffResult = open(url, WECOM_PACKAGE)

    fun isInstalled(packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    fun share(url: String): Boolean {
        if (!SignUrl.isValid(url)) return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        val chooser = Intent.createChooser(send, "分享签到链接").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun shareToWeChat(url: String): Boolean {
        if (!SignUrl.isValid(url)) return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            setPackage(WECHAT_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(send)
            true
        }.getOrDefault(false)
    }

    private fun open(url: String, packageName: String): HandoffResult {
        if (!SignUrl.isValid(url)) return HandoffResult.InvalidUrl
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            // Do not pre-check resolveActivity: Honor returns null for VIEW even
            // though startActivity reaches the system WeChat/WeChat Favorites
            // disambiguation. The launch itself is the authority.
            context.startActivity(intent)
            HandoffResult.Opened
        } catch (_: ActivityNotFoundException) {
            if (isInstalled(packageName)) HandoffResult.Blocked else HandoffResult.NotInstalled
        } catch (_: SecurityException) {
            HandoffResult.Blocked
        } catch (_: Exception) {
            HandoffResult.Blocked
        }
    }

    companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
        const val WECOM_PACKAGE = "com.tencent.wework"
    }
}
