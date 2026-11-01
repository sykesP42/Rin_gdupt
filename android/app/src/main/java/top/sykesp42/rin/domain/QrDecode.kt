package top.sykesp42.rin.domain

import android.graphics.BitmapFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer

/** Decodes a square QR matrix; malformed or non-QR input returns null. */
internal fun decodeQrMatrix(matrix: BitMatrix): String? = runCatching {
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height) { index ->
        val x = index % width
        val y = index / width
        if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    val source = RGBLuminanceSource(width, height, pixels)
    val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
    )
    MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
}.getOrNull()

/**
 * Decodes the school's QR image fetched through the threshold-gated local API.
 * The caller still validates the resulting URL with [SignUrl] before handoff.
 */
fun decodeQrPng(png: ByteArray): String? {
    val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val source = RGBLuminanceSource(width, height, pixels)
    val binary = BinaryBitmap(HybridBinarizer(source))
    return runCatching {
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
        )
        MultiFormatReader().decode(binary, hints).text
    }.getOrNull()
}
