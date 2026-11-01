package top.sykesp42.rin.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.WindowManager
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/**
 * Non-sensitive device context for license support. Deliberately excludes
 * IMEI/serial/Android ID, contacts, location, installed apps and ad IDs.
 */
fun collectLicenseDeviceInfo(context: Context): Map<String, String> {
    val metrics = context.resources.displayMetrics
    val configuration = context.resources.configuration
    val locale = Locale.getDefault()
    val locales = if (Build.VERSION.SDK_INT >= 24) {
        (0 until configuration.locales.size()).map { configuration.locales[it].toLanguageTag() }
    } else {
        listOf(locale.toLanguageTag())
    }
    val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
    val versionCode = when {
        packageInfo == null -> ""
        Build.VERSION.SDK_INT >= 28 -> packageInfo.longVersionCode.toString()
        else -> @Suppress("DEPRECATION") packageInfo.versionCode.toString()
    }
    val totalRam = runCatching {
        val manager = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        manager?.getMemoryInfo(info)
        (info.totalMem / (1024L * 1024L)).toString()
    }.getOrDefault("")
    val inches = runCatching {
        val x = metrics.widthPixels / metrics.xdpi
        val y = metrics.heightPixels / metrics.ydpi
        String.format(Locale.US, "%.1f", kotlin.math.hypot(x.toDouble(), y.toDouble()))
    }.getOrDefault("")
    val refreshRate = runCatching {
        val windowManager = context.getSystemService(WindowManager::class.java)
        val rate = windowManager?.defaultDisplay?.refreshRate ?: return@runCatching ""
        String.format(Locale.US, "%.0f", rate)
    }.getOrDefault("")
    val offsetMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
    return linkedMapOf(
        "manufacturer" to Build.MANUFACTURER.orEmpty(),
        "brand" to Build.BRAND.orEmpty(),
        "model" to Build.MODEL.orEmpty(),
        "device" to Build.DEVICE.orEmpty(),
        "product" to Build.PRODUCT.orEmpty(),
        "hardware" to Build.HARDWARE.orEmpty(),
        "board" to Build.BOARD.orEmpty(),
        "android" to Build.VERSION.RELEASE.orEmpty(),
        "sdkInt" to Build.VERSION.SDK_INT.toString(),
        "securityPatch" to Build.VERSION.SECURITY_PATCH.orEmpty(),
        "buildType" to Build.TYPE.orEmpty(),
        "buildTags" to Build.TAGS.orEmpty(),
        "incremental" to Build.VERSION.INCREMENTAL.orEmpty(),
        "abi" to Build.SUPPORTED_ABIS.joinToString(","),
        "screenPx" to "${metrics.widthPixels}x${metrics.heightPixels}",
        "screenDp" to "${configuration.screenWidthDp}x${configuration.screenHeightDp}",
        "densityDpi" to metrics.densityDpi.toString(),
        "screenInches" to inches,
        "displayHz" to refreshRate,
        "locale" to locale.toLanguageTag(),
        "locales" to locales.joinToString(","),
        "timezone" to TimeZone.getDefault().id,
        "timezoneOffsetMinutes" to offsetMinutes.toString(),
        "ramTotalMb" to totalRam,
        "packageName" to context.packageName,
        "firstInstallTime" to packageInfo?.firstInstallTime
            ?.let { Instant.ofEpochMilli(it).toString() }.orEmpty(),
        "lastUpdateTime" to packageInfo?.lastUpdateTime
            ?.let { Instant.ofEpochMilli(it).toString() }.orEmpty(),
        "appVersion" to packageInfo?.versionName.orEmpty(),
        "appVersionCode" to versionCode
    )
}
