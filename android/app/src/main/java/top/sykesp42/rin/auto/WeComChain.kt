package top.sykesp42.rin.auto

/**
 * Node-exact WeCom album-scan chain. WeCom exposes a fully readable
 * accessibility tree (unlike WeChat on this device), so every step matches
 * resource-ids and localized text verified on the real phone (HONOR Magic6,
 * WeCom 4.x EN UI, 2026-10-24):
 *
 *   main "+" (id/ns8) → "Scan QR Code" → "Album" (id/lk7) → first grid cell
 *   (id/z3, WeCom's own gallery, newest photo first)
 *
 * The QR PNG is published to MediaStore beforehand (GalleryPublisher), so the
 * newest cell is always the current sign code.
 */
object WeComChain {
    const val WECOM_PACKAGE = "com.tencent.wework"

    /** Optional popups (e.g. upgrade dialog) dismissed only if present. */
    internal fun dismissibles() = listOf(
        NodeSpec(textEquals = listOf("Cancel", "取消"), idContains = listOf("dha"))
    )

    /** WeCom Messages home: the top-right "+" opens the action menu. */
    internal fun homeMarkers() = listOf(
        NodeSpec(idContains = listOf("ns8")),
        NodeSpec(textEquals = listOf("Messages", "消息"))
    )

    internal fun plusButtons() = listOf(
        NodeSpec(idContains = listOf("ns8"))
    )

    internal fun scanMenu() = listOf(
        NodeSpec(textEquals = listOf("Scan QR Code", "扫一扫"))
    )

    internal fun albumButtons() = listOf(
        NodeSpec(textEquals = listOf("Album", "相册")),
        NodeSpec(idContains = listOf("lk7", "lk6"))
    )

    /** First cell of WeCom's own album grid = newest photo = our QR. */
    internal fun newestAlbumCells() = listOf(
        NodeSpec(idContains = listOf("z3"))
    )

    /** The photo picker preview's confirm button; triggers QR recognition. */
    internal fun doneButtons() = listOf(
        NodeSpec(textEquals = listOf("Done", "完成")),
        NodeSpec(idContains = listOf("bqm"))
    )

    fun wecomAlbumScanChain(): List<ChainStep> = listOf(
        ChainStep.LaunchApp(WECOM_PACKAGE),
        ChainStep.WaitForWindow(WECOM_PACKAGE, timeoutMs = 8_000),
        // WeCom resumes wherever it was left: in the scanner (Album visible →
        // done), on Messages (walk "+" → Scan QR Code), or deep inside (back).
        ChainStep.EnsureScreen(
            entryMatchers = listOf(plusButtons(), scanMenu()),
            goalMatchers = albumButtons(),
            dismissMatchers = dismissibles(),
            maxBacks = 3,
            timeoutMs = 10_000
        ),
        ChainStep.ClickAny(albumButtons(), timeoutMs = 4_000),
        ChainStep.ClickAny(newestAlbumCells(), timeoutMs = 5_000),
        ChainStep.ClickAny(doneButtons(), timeoutMs = 4_000)
    )
}
