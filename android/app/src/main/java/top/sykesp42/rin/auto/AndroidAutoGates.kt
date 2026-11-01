package top.sykesp42.rin.auto

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import java.time.Instant

/** Production [AutoGates] backed by Android system services. */
class AndroidAutoGates(private val context: Context) : AutoGates {

    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    override fun driverConnected(): Boolean = AutoRuntime.isConnected()

    override fun screenInteractive(): Boolean = power?.isInteractive ?: false

    override fun keyguardLocked(): Boolean = keyguard?.isKeyguardLocked ?: true

    override fun weChatInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(WeChatChain.WECHAT_PACKAGE, 0)
    }.isSuccess

    override fun weComInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(WeComChain.WECOM_PACKAGE, 0)
    }.isSuccess

    override fun now(): Instant = Instant.now()
}
