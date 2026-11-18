package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R
import com.example.data.model.StudioPreset
import com.example.data.model.Vehicle
import com.example.data.model.VehicleStatus
import com.example.ui.theme.AmberReserved
import com.example.ui.theme.AmberReservedContainer
import com.example.ui.theme.EmeraldAvailable
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.SlateSold
import com.example.ui.theme.SlateSoldContainer
import com.google.firebase.Timestamp
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val decodedDataUrlCache = object : LruCache<String, ByteArray>(48) {}

private val usdFormatter: NumberFormat
    get() = NumberFormat.getCurrencyInstance(Locale.US).apply {
        maximumFractionDigits = 0
    }

fun formatCurrency(amount: Long): String = usdFormatter.format(amount)

fun formatCompactCurrency(amount: Long): String = when {
    amount >= 1_000_000L -> String.format(Locale.US, "$%.2fM", amount / 1_000_000.0)
    amount >= 10_000L -> String.format(Locale.US, "$%.1fK", amount / 1_000.0)
    else -> formatCurrency(amount)
}

fun formatMileageDisplay(mileage: String): String {
    val trimmed = mileage.trim()
    if (trimmed.isEmpty()) return "0 km"
    return if (trimmed.all { it.isDigit() || it == ',' }) "$trimmed km" else trimmed
}

fun formatTimestamp(timestamp: Timestamp?, fallbackString: String = ""): String {
    val date = timestamp?.toDate()
    if (date != null) {
        val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return formatter.format(date)
    }
    return fallbackString.takeIf { it.isNotBlank() }?.take(10) ?: "Recently updated"
}

fun formatEpochMillis(millis: Long): String {
    if (millis <= 0L) return "Just now"
    val formatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    return formatter.format(java.util.Date(millis))
}

@DrawableRes
fun StudioPreset.studioDrawableRes(): Int = when (this) {
    StudioPreset.SEDAN -> R.drawable.img_vehicle_sedan
    StudioPreset.SUV -> R.drawable.img_vehicle_suv
    StudioPreset.TRUCK -> R.drawable.img_vehicle_truck
    StudioPreset.COUPE -> R.drawable.img_vehicle_coupe
}

private const val PLACEHOLDER_IMAGE_URL = "https://alifcarimport.com/placeholder.jpg"

fun decodeImageModel(imageUrl: String?): Any? {
    val trimmed = imageUrl?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed.equals(PLACEHOLDER_IMAGE_URL, ignoreCase = true)) {
        return null
    }
    if (trimmed.startsWith("data:image", ignoreCase = true)) {
        val cacheKey = "${trimmed.length}:${trimmed.hashCode()}"
        decodedDataUrlCache.get(cacheKey)?.let { return it }
        val commaIndex = trimmed.indexOf(',')
        if (commaIndex != -1 && commaIndex + 1 < trimmed.length) {
            val base64Part = trimmed.substring(commaIndex + 1)
            val decoded = runCatching { Base64.decode(base64Part, Base64.DEFAULT) }.getOrNull()
            if (decoded != null && decoded.isNotEmpty()) {
                decodedDataUrlCache.put(cacheKey, decoded)
                return decoded
            }
        }
    }
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return trimmed
    }
    return null
}

fun Vehicle.resolveImageModel(): Any? = decodeImageModel(imageUrl)

private val encodedDrawableCache = java.util.concurrent.ConcurrentHashMap<Int, String>()

fun encodePickedPhotoAsDataUrl(
    context: Context,
    uri: Uri,
    maxDimension: Int = 800,
    targetMaxBytes: Int = 120_000
): String? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val sampled = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null
        compressBitmapToDataUrl(
            bitmap = sampled,
            maxDimension = maxDimension.toFloat(),
            targetMaxBytes = targetMaxBytes
        )
    } catch (_: Exception) {
        null
    }
}

fun encodeGalleryPhotoAsDataUrl(context: Context, uri: Uri): String? =
    encodePickedPhotoAsDataUrl(
        context = context,
        uri = uri,
        maxDimension = 640,
        targetMaxBytes = 55_000
    )

fun encodeDrawableAsDataUrl(context: Context, @DrawableRes resId: Int): String? {
    encodedDrawableCache[resId]?.let { return it }
    return try {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 2
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeResource(context.resources, resId, opts)
            ?: BitmapFactory.decodeResource(context.resources, resId)
            ?: return null
        compressBitmapToDataUrl(
            bitmap = bitmap,
            maxDimension = 640f,
            targetMaxBytes = 55_000
        )?.also { encodedDrawableCache[resId] = it }
    } catch (_: Exception) {
        null
    }
}

private fun calculateInSampleSize(width: Int, height: Int, reqSize: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var inSampleSize = 1
    val largest = maxOf(width, height)
    while (largest / (inSampleSize * 2) >= reqSize) {
        inSampleSize *= 2
    }
    return inSampleSize
}

private fun compressBitmapToDataUrl(
    bitmap: Bitmap,
    maxDimension: Float = 800f,
    targetMaxBytes: Int = 120_000
): String? {
    val scale = minOf(maxDimension / bitmap.width, maxDimension / bitmap.height, 1f)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        ).also {
            if (it !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        }
    } else {
        bitmap
    }

    var quality = 74
    var bytes: ByteArray
    val out = ByteArrayOutputStream(targetMaxBytes)
    do {
        out.reset()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bytes = out.toByteArray()
        quality -= 14
    } while (bytes.size > targetMaxBytes && quality >= 32)

    if (!scaled.isRecycled) scaled.recycle()

    val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
    val dataUrl = "data:image/jpeg;base64,$encoded"
    return dataUrl.takeIf { it.length <= 540_000 }
}

data class StatusColors(
    val foreground: Color,
    val background: Color
)

fun VehicleStatus.colors(): StatusColors = when (this) {
    VehicleStatus.AVAILABLE -> StatusColors(EmeraldAvailable, EmeraldContainer)
    VehicleStatus.RESERVED -> StatusColors(AmberReserved, AmberReservedContainer)
    VehicleStatus.SOLD -> StatusColors(SlateSold, SlateSoldContainer)
}
