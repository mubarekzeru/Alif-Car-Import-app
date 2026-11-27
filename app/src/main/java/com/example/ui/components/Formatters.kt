package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import com.example.data.model.Vehicle
import com.example.data.model.VehicleStatus
import com.example.ui.theme.AmberReserved
import com.example.ui.theme.AmberReservedContainer
import com.example.ui.theme.EmeraldAvailable
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.SlateSold
import com.example.ui.theme.SlateSoldContainer
import coil.request.ImageRequest
import com.google.firebase.Timestamp
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val decodedDataUrlCache = object : LruCache<String, ByteArray>(48) {}

private val etbNumberFormatter: NumberFormat
    get() = NumberFormat.getIntegerInstance(Locale.US)

fun formatCurrency(amount: Long): String = "ETB ${etbNumberFormatter.format(amount)}"

fun formatOptionalCurrency(amount: Long): String? =
    amount.takeIf { it > 0L }?.let { formatCurrency(it) }

fun formatCompactCurrency(amount: Long): String = when {
    amount >= 1_000_000L -> String.format(Locale.US, "ETB %.2fM", amount / 1_000_000.0)
    amount >= 10_000L -> String.format(Locale.US, "ETB %.1fK", amount / 1_000.0)
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

private const val WEBSITE_BASE_URL = "https://alifcarimport.com"
private const val GITHUB_PAGES_ORIGIN = "https://mubarekzeru.github.io"
private const val GITHUB_PAGES_BASE_URL = "https://mubarekzeru.github.io/AlifCarImport"
private const val GITHUB_RAW_BASE_URL = "https://raw.githubusercontent.com/mubarekzeru/AlifCarImport/main"
private const val FIREBASE_STORAGE_BUCKET_PRIMARY = "alif-car-import.firebasestorage.app"
private const val FIREBASE_STORAGE_BUCKET_LEGACY = "alif-car-import.appspot.com"
private const val PLACEHOLDER_IMAGE_URL = "https://alifcarimport.com/placeholder.jpg"
private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

private val GOOGLE_DRIVE_FILE_REGEX = Regex("""drive\.google\.com/file/d/([^/?#]+)""", RegexOption.IGNORE_CASE)
private val GOOGLE_DRIVE_OPEN_REGEX = Regex("""drive\.google\.com/(?:open|uc|thumbnail)\?.*[?&]?id=([^&#]+)""", RegexOption.IGNORE_CASE)
private val GITHUB_BLOB_REGEX = Regex(
    """^https?://github\.com/([^/]+)/([^/]+)/(?:blob|raw)/([^/]+)/(.+)$""",
    RegexOption.IGNORE_CASE
)
private val STORAGE_GOOGLEAPIS_REGEX = Regex(
    """^https?://storage\.googleapis\.com/([^/]+)/(.+)$""",
    RegexOption.IGNORE_CASE
)
private val IMAGE_EXTENSION_REGEX = Regex(
    """\.(?:jpe?g|png|webp|gif|avif|bmp|heic|heif|jfif)(?:[?#].*)?$""",
    RegexOption.IGNORE_CASE
)

private fun sanitizeRawImageInput(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    var cleaned = raw.trim()
        .trim('"', '\'', '`')
        .trim()
    if (cleaned.startsWith("url(", ignoreCase = true) && cleaned.endsWith(")")) {
        cleaned = cleaned.substring(4, cleaned.length - 1).trim().trim('"', '\'')
    }
    if (!cleaned.startsWith("data:", ignoreCase = true)) {
        cleaned = cleaned
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&#x2F;", "/", ignoreCase = true)
            .replace("&#47;", "/", ignoreCase = true)
            .replace('\\', '/')
    }
    return cleaned.trim()
}

private fun isPlaceholderOrInvalid(value: String): Boolean {
    if (value.isEmpty()) return true
    val lower = value.lowercase(Locale.US)
    return lower == "null" ||
        lower == "undefined" ||
        lower == "none" ||
        lower == "n/a" ||
        lower == PLACEHOLDER_IMAGE_URL ||
        lower == "$GITHUB_PAGES_BASE_URL/placeholder.jpg".lowercase(Locale.US) ||
        lower == "/placeholder.jpg" ||
        lower == "placeholder.jpg" ||
        lower == "./placeholder.jpg" ||
        lower.endsWith("/placeholder.jpg") ||
        lower.endsWith("/placeholder.png") ||
        lower.startsWith("blob:")
}

private fun detectRawBase64DataUrl(value: String): String? {
    val compact = value.replace("\\s+".toRegex(), "")
    if (compact.length < 64) return null
    val mime = when {
        compact.startsWith("/9j/") -> "image/jpeg"
        compact.startsWith("iVBORw0KGgo") -> "image/png"
        compact.startsWith("UklGR") -> "image/webp"
        compact.startsWith("R0lGOD") -> "image/gif"
        else -> return null
    }
    return "data:$mime;base64,$compact"
}

private fun encodeUrlSafe(url: String): String =
    url.replace(" ", "%20")

private fun buildFirebaseStorageMediaUrl(bucket: String, objectPath: String): String {
    val cleanPath = objectPath.trimStart('/')
    val encodedPath = runCatching {
        URLEncoder.encode(cleanPath, "UTF-8").replace("+", "%20")
    }.getOrDefault(cleanPath)
    return "https://firebasestorage.googleapis.com/v0/b/$bucket/o/$encodedPath?alt=media"
}

private fun MutableList<String>.addUnique(url: String?) {
    val candidate = url?.trim()?.takeIf { it.isNotEmpty() } ?: return
    if (candidate !in this) add(candidate)
}

fun resolveCandidateImageUrls(imageUrl: String?): List<String> {
    val cleaned = sanitizeRawImageInput(imageUrl)
    if (isPlaceholderOrInvalid(cleaned)) return emptyList()

    if (cleaned.startsWith("data:", ignoreCase = true)) {
        return listOf(cleaned)
    }

    detectRawBase64DataUrl(cleaned)?.let { dataUrl ->
        return listOf(dataUrl)
    }

    val candidates = mutableListOf<String>()

    // 1. Next.js image optimizer URLs (/_next/image?url=... or https://.../_next/image?url=...)
    if (cleaned.contains("/_next/image?", ignoreCase = true)) {
        val query = cleaned.substringAfter('?', "")
        val rawTarget = query.split('&')
            .firstOrNull { it.startsWith("url=", ignoreCase = true) }
            ?.substringAfter('=')
        if (!rawTarget.isNullOrBlank()) {
            val decoded = runCatching { URLDecoder.decode(rawTarget, "UTF-8") }.getOrNull()
            if (!decoded.isNullOrBlank() && !decoded.contains("/_next/image?", ignoreCase = true)) {
                resolveCandidateImageUrls(decoded).forEach { candidates.addUnique(it) }
            }
        }
        val fullOptimizer = when {
            cleaned.startsWith("https://", ignoreCase = true) -> cleaned
            cleaned.startsWith("http://", ignoreCase = true) -> "https://${cleaned.substring(7)}"
            cleaned.startsWith("/") -> "$WEBSITE_BASE_URL$cleaned"
            else -> "$WEBSITE_BASE_URL/$cleaned"
        }
        candidates.addUnique(encodeUrlSafe(fullOptimizer))
        return candidates
    }

    // 2. Firebase Storage gs://bucket/path
    if (cleaned.startsWith("gs://", ignoreCase = true)) {
        val withoutScheme = cleaned.substring(5)
        val slashIdx = withoutScheme.indexOf('/')
        if (slashIdx > 0 && slashIdx + 1 < withoutScheme.length) {
            val bucket = withoutScheme.substring(0, slashIdx)
            val objectPath = withoutScheme.substring(slashIdx + 1)
            candidates.addUnique(buildFirebaseStorageMediaUrl(bucket, objectPath))
        }
        return candidates
    }

    // 3. Google Drive share / open / thumbnail links
    val driveId = GOOGLE_DRIVE_FILE_REGEX.find(cleaned)?.groupValues?.getOrNull(1)
        ?: GOOGLE_DRIVE_OPEN_REGEX.find(cleaned)?.groupValues?.getOrNull(1)
    if (!driveId.isNullOrBlank()) {
        candidates.addUnique("https://drive.google.com/thumbnail?id=$driveId&sz=w1200")
        candidates.addUnique("https://lh3.googleusercontent.com/d/$driveId")
        candidates.addUnique("https://drive.google.com/uc?export=view&id=$driveId")
        return candidates
    }

    // 4. GitHub blob/raw web URLs -> raw.githubusercontent.com
    GITHUB_BLOB_REGEX.find(cleaned)?.let { match ->
        val (owner, repo, branch, path) = match.destructured
        candidates.addUnique(encodeUrlSafe("https://raw.githubusercontent.com/$owner/$repo/$branch/$path"))
    }

    // 5. Google Cloud Storage URL -> also add Firebase Storage media endpoint fallback
    STORAGE_GOOGLEAPIS_REGEX.find(cleaned)?.let { match ->
        val (bucket, objectPath) = match.destructured
        candidates.addUnique(encodeUrlSafe(cleaned.replaceFirst("http://", "https://", ignoreCase = true)))
        candidates.addUnique(buildFirebaseStorageMediaUrl(bucket, objectPath.substringBefore('?')))
        return candidates
    }

    // 6. Dropbox share links -> direct raw stream
    if (cleaned.contains("dropbox.com/", ignoreCase = true)) {
        val direct = when {
            cleaned.contains("dl=0", ignoreCase = true) -> cleaned.replace("dl=0", "raw=1", ignoreCase = true)
            cleaned.contains("raw=1", ignoreCase = true) -> cleaned
            cleaned.contains('?') -> "$cleaned&raw=1"
            else -> "$cleaned?raw=1"
        }
        candidates.addUnique(encodeUrlSafe(direct.replaceFirst("http://", "https://", ignoreCase = true)))
        return candidates
    }

    // 7. Full HTTP/HTTPS or protocol-relative URLs
    if (cleaned.startsWith("https://", ignoreCase = true) ||
        cleaned.startsWith("http://", ignoreCase = true) ||
        cleaned.startsWith("//")
    ) {
        val httpsUrl = when {
            cleaned.startsWith("//") -> "https:$cleaned"
            cleaned.startsWith("http://", ignoreCase = true) -> "https://${cleaned.substring(7)}"
            else -> cleaned
        }
        val encodedHttps = encodeUrlSafe(httpsUrl)

        // Ensure Firebase Storage URLs include alt=media
        if (encodedHttps.contains("firebasestorage.googleapis.com/v0/b/", ignoreCase = true) &&
            !encodedHttps.contains("alt=media", ignoreCase = true)
        ) {
            val withMedia = if (encodedHttps.contains('?')) "$encodedHttps&alt=media" else "$encodedHttps?alt=media"
            candidates.addUnique(withMedia)
        }

        candidates.addUnique(encodedHttps)

        // Cross-mirror between alifcarimport.com and mubarekzeru.github.io/AlifCarImport
        val lower = encodedHttps.lowercase(Locale.US)
        when {
            lower.startsWith("$GITHUB_PAGES_BASE_URL/".lowercase(Locale.US)) -> {
                val relPath = encodedHttps.substring(GITHUB_PAGES_BASE_URL.length + 1)
                candidates.addUnique("$WEBSITE_BASE_URL/$relPath")
                candidates.addUnique("$GITHUB_RAW_BASE_URL/public/$relPath")
                candidates.addUnique("$GITHUB_RAW_BASE_URL/$relPath")
            }
            lower.startsWith("$WEBSITE_BASE_URL/") -> {
                val relPath = encodedHttps.substring(WEBSITE_BASE_URL.length + 1)
                if (relPath.isNotEmpty()) {
                    candidates.addUnique("$GITHUB_PAGES_BASE_URL/$relPath")
                    val encodedNextTarget = runCatching {
                        URLEncoder.encode("/$relPath", "UTF-8").replace("+", "%20")
                    }.getOrNull()
                    if (!encodedNextTarget.isNullOrBlank()) {
                        candidates.addUnique("$WEBSITE_BASE_URL/_next/image?url=$encodedNextTarget&w=1080&q=75")
                    }
                }
            }
            lower.startsWith("https://www.alifcarimport.com/") -> {
                val relPath = encodedHttps.substring("https://www.alifcarimport.com/".length)
                if (relPath.isNotEmpty()) {
                    candidates.addUnique("$WEBSITE_BASE_URL/$relPath")
                    candidates.addUnique("$GITHUB_PAGES_BASE_URL/$relPath")
                }
            }
        }

        if (cleaned.startsWith("http://", ignoreCase = true) &&
            !lower.startsWith("$WEBSITE_BASE_URL/") &&
            !lower.startsWith("https://www.alifcarimport.com/") &&
            !lower.startsWith("$GITHUB_PAGES_ORIGIN/")
        ) {
            candidates.addUnique(encodeUrlSafe(cleaned))
        }
        return candidates
    }

    // 8. Domain without scheme (e.g., mubarekzeru.github.io/..., alifcarimport.com/...)
    val lowerClean = cleaned.lowercase(Locale.US)
    if (lowerClean.startsWith("alifcarimport.com/") ||
        lowerClean.startsWith("www.alifcarimport.com/") ||
        lowerClean.startsWith("mubarekzeru.github.io/") ||
        lowerClean.startsWith("firebasestorage.googleapis.com/") ||
        lowerClean.startsWith("raw.githubusercontent.com/")
    ) {
        return resolveCandidateImageUrls("https://$cleaned")
    }

    // 9. Relative paths hosted on mubarekzeru.github.io/AlifCarImport or alifcarimport.com
    val withoutDot = cleaned.removePrefix("./")
    val withoutLeadingSlash = withoutDot.trimStart('/')
    val looksLikeRelativePath = cleaned.startsWith("/") ||
        cleaned.startsWith("./") ||
        cleaned.contains('/') ||
        IMAGE_EXTENSION_REGEX.containsMatchIn(cleaned)

    if (!looksLikeRelativePath || withoutLeadingSlash.isEmpty()) {
        return emptyList()
    }

    val encodedRel = encodeUrlSafe(withoutLeadingSlash)
    val relLower = encodedRel.lowercase(Locale.US)

    if (relLower.startsWith("alifcarimport/")) {
        val innerPath = encodedRel.substring("alifcarimport/".length)
        candidates.addUnique("$GITHUB_PAGES_ORIGIN/$encodedRel")
        candidates.addUnique("$WEBSITE_BASE_URL/$innerPath")
        candidates.addUnique("$GITHUB_RAW_BASE_URL/public/$innerPath")
        candidates.addUnique("$GITHUB_RAW_BASE_URL/$innerPath")
        return candidates
    }

    if (relLower.startsWith("uploads/")) {
        candidates.addUnique("$WEBSITE_BASE_URL/$encodedRel")
        candidates.addUnique("$GITHUB_PAGES_BASE_URL/$encodedRel")
        candidates.addUnique("$GITHUB_PAGES_ORIGIN/$encodedRel")
    } else {
        candidates.addUnique("$GITHUB_PAGES_BASE_URL/$encodedRel")
        candidates.addUnique("$WEBSITE_BASE_URL/$encodedRel")
        candidates.addUnique("$GITHUB_PAGES_ORIGIN/$encodedRel")
    }

    // Bare image filename fallback folders (e.g. "song-plus.jpg")
    if (!encodedRel.contains('/')) {
        candidates.addUnique("$WEBSITE_BASE_URL/uploads/$encodedRel")
        candidates.addUnique("$GITHUB_PAGES_BASE_URL/images/$encodedRel")
        candidates.addUnique("$GITHUB_PAGES_BASE_URL/cars/$encodedRel")
        candidates.addUnique("$GITHUB_PAGES_BASE_URL/uploads/$encodedRel")
    }

    // GitHub raw and Firebase Storage object path fallbacks
    candidates.addUnique("$GITHUB_RAW_BASE_URL/public/$encodedRel")
    candidates.addUnique(buildFirebaseStorageMediaUrl(FIREBASE_STORAGE_BUCKET_PRIMARY, withoutLeadingSlash))
    candidates.addUnique(buildFirebaseStorageMediaUrl(FIREBASE_STORAGE_BUCKET_LEGACY, withoutLeadingSlash))

    return candidates
}

fun normalizeWebsiteImageUrl(imageUrl: String?): String? =
    resolveCandidateImageUrls(imageUrl).firstOrNull()

private fun decodeDataUrlBytes(dataUrl: String): ByteArray? {
    val commaIndex = dataUrl.indexOf(',')
    if (commaIndex == -1 || commaIndex + 1 >= dataUrl.length) return null
    var payload = dataUrl.substring(commaIndex + 1).replace("\\s+".toRegex(), "")
    if (payload.isEmpty()) return null
    if (payload.contains('%')) {
        runCatching { URLDecoder.decode(payload, "UTF-8") }
            .getOrNull()
            ?.replace("\\s+".toRegex(), "")
            ?.takeIf { it.isNotEmpty() }
            ?.let { payload = it }
    }
    val mod = payload.length % 4
    if (mod in 2..3) {
        payload += "=".repeat(4 - mod)
    }
    return runCatching { Base64.decode(payload, Base64.DEFAULT) }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }
        ?: runCatching { Base64.decode(payload, Base64.URL_SAFE) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
}

fun decodeImageModel(imageUrl: String?): Any? {
    val normalized = normalizeWebsiteImageUrl(imageUrl) ?: return null
    if (normalized.startsWith("data:", ignoreCase = true)) {
        val cacheKey = "${normalized.length}:${normalized.hashCode()}"
        decodedDataUrlCache.get(cacheKey)?.let { return it }
        val decoded = decodeDataUrlBytes(normalized)
        if (decoded != null) {
            decodedDataUrlCache.put(cacheKey, decoded)
            return decoded
        }
        return null
    }
    return normalized
}

/**
 * Returns distinct photo slots for a vehicle (primary image + gallery images),
 * where each slot contains ordered mirror/fallback candidate URLs to try on load error.
 */
fun Vehicle.resolvedPhotoSlots(): List<List<String>> {
    val seenPrimary = LinkedHashSet<String>()
    val slots = mutableListOf<List<String>>()
    val rawInputs = buildList {
        add(imageUrl)
        addAll(galleryImages)
    }
    rawInputs.forEach { raw ->
        val candidates = resolveCandidateImageUrls(raw)
        val first = candidates.firstOrNull()
        if (first != null && seenPrimary.add(first)) {
            slots.add(candidates)
        }
    }
    return slots
}

/**
 * Flattened, deduplicated list of all candidate URLs across the vehicle's primary and gallery images.
 */
fun Vehicle.allResolvedPhotoUrls(): List<String> = buildList {
    resolvedPhotoSlots().forEach { slot ->
        slot.forEach { url ->
            if (url !in this) add(url)
        }
    }
}

fun buildAppImageRequest(
    context: Context,
    data: Any,
    memoryCacheKey: String? = null,
    width: Int? = null,
    height: Int? = null,
    crossfadeMillis: Int = 180
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(data)
        .apply {
            if (data is String && (data.startsWith("http://", true) || data.startsWith("https://", true))) {
                addHeader("User-Agent", BROWSER_USER_AGENT)
                addHeader("Accept", "image/webp,image/png,image/jpeg,image/*;q=0.9,*/*;q=0.8")
                val lower = data.lowercase(Locale.US)
                when {
                    lower.startsWith("https://alifcarimport.com/") ||
                        lower.startsWith("https://www.alifcarimport.com/") ->
                        addHeader("Referer", "$WEBSITE_BASE_URL/")
                    lower.startsWith("https://mubarekzeru.github.io/") ->
                        addHeader("Referer", "$GITHUB_PAGES_BASE_URL/")
                }
            }
            if (!memoryCacheKey.isNullOrBlank()) {
                memoryCacheKey(memoryCacheKey)
            }
            if (width != null && height != null) {
                size(width, height)
            }
            if (crossfadeMillis > 0) {
                crossfade(crossfadeMillis)
            }
        }
        .build()
}

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
