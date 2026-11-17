package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
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

@DrawableRes
fun StudioPreset.studioDrawableRes(): Int = when (this) {
    StudioPreset.SEDAN -> R.drawable.img_vehicle_sedan
    StudioPreset.SUV -> R.drawable.img_vehicle_suv
    StudioPreset.TRUCK -> R.drawable.img_vehicle_truck
    StudioPreset.COUPE -> R.drawable.img_vehicle_coupe
}

fun decodeImageModel(imageUrl: String?, fallbackPreset: StudioPreset = StudioPreset.SUV): Any {
    val trimmed = imageUrl?.trim().orEmpty()
    if (trimmed.startsWith("data:image", ignoreCase = true)) {
        val commaIndex = trimmed.indexOf(',')
        if (commaIndex != -1 && commaIndex + 1 < trimmed.length) {
            val base64Part = trimmed.substring(commaIndex + 1)
            val decoded = runCatching { Base64.decode(base64Part, Base64.DEFAULT) }.getOrNull()
            if (decoded != null && decoded.isNotEmpty()) {
                return decoded
            }
        }
    }
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return trimmed
    }
    return fallbackPreset.studioDrawableRes()
}

fun Vehicle.resolveImageModel(): Any = decodeImageModel(imageUrl)

fun encodePickedPhotoAsDataUrl(context: Context, uri: Uri): String? {
    return try {
        val original = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null
        compressBitmapToDataUrl(original)
    } catch (_: Exception) {
        null
    }
}

fun encodeDrawableAsDataUrl(context: Context, @DrawableRes resId: Int): String? {
    return try {
        val bitmap = BitmapFactory.decodeResource(context.resources, resId) ?: return null
        compressBitmapToDataUrl(bitmap)
    } catch (_: Exception) {
        null
    }
}

private fun compressBitmapToDataUrl(bitmap: Bitmap): String? {
    val maxDimension = 960f
    val scale = minOf(maxDimension / bitmap.width, maxDimension / bitmap.height, 1f)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }

    var quality = 80
    var bytes: ByteArray
    do {
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bytes = out.toByteArray()
        quality -= 15
    } while (bytes.size > 320_000 && quality >= 35)

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
