package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class TikTokPreviewMetadata(
    val thumbnailUrl: String,
    val title: String = "",
    val authorName: String = "",
    val canonicalUrl: String = ""
)

/**
 * Resolves real video cover thumbnails from TikTok links (both canonical
 * `www.tiktok.com/@.../video/...` URLs and short `vm.tiktok.com` / `vt.tiktok.com` / `/t/` links)
 * using TikTok's public oEmbed API with an HTML OpenGraph fallback.
 */
object TikTokThumbnailResolver {
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    private const val FAILURE_COOLDOWN_MS = 90_000L
    private const val MAX_HTML_BYTES = 512 * 1024

    private val urlInTextRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val bareTikTokRegex = Regex(
        """(?:www\.|m\.|vm\.|vt\.)?tiktok\.com/[^\s<>"']+""",
        RegexOption.IGNORE_CASE
    )
    private val ogImagePatterns = listOf(
        Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+name=["']twitter:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+name=["']twitter:image["']""", RegexOption.IGNORE_CASE),
        Regex(""""originCover"\s*:\s*"((?:\\.|[^"\\])+)""""),
        Regex(""""cover"\s*:\s*"((?:\\.|[^"\\])+)"""")
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val cache = ConcurrentHashMap<String, TikTokPreviewMetadata>()
    private val failedTimestamps = ConcurrentHashMap<String, Long>()

    fun isTikTokHost(host: String): Boolean {
        val lower = host.trim().lowercase(Locale.US)
        return lower == "tiktok.com" || lower.endsWith(".tiktok.com")
    }

    fun isTikTokCdnHost(host: String): Boolean {
        val lower = host.trim().lowercase(Locale.US)
        return lower == "tiktok.com" ||
            lower.endsWith(".tiktok.com") ||
            lower.endsWith(".tiktokcdn.com") ||
            lower.endsWith(".tiktokcdn-us.com") ||
            lower.endsWith(".tiktokcdn-eu.com") ||
            lower.endsWith(".tiktokv.com") ||
            lower.endsWith(".muscdn.com") ||
            lower.endsWith(".ibyteimg.com") ||
            lower.endsWith(".ttwstatic.com")
    }

    /**
     * Extracts and validates a TikTok HTTPS URL from raw user input (including share text
     * copied from the TikTok app). Returns null for non-TikTok or malformed URLs.
     */
    fun normalizeTikTokUrl(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val extracted = urlInTextRegex.find(trimmed)?.value
            ?: bareTikTokRegex.find(trimmed)?.value?.let { "https://$it" }
            ?: if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                trimmed
            } else {
                "https://$trimmed"
            }

        val cleaned = extracted.trimEnd('.', ',', ';', ')', ']', '"', '\'')
        val parsed = cleaned.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "https" && parsed.scheme != "http") return null
        if (!isTikTokHost(parsed.host)) return null
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null

        val httpsUrl = parsed.newBuilder()
            .scheme("https")
            .fragment(null)
            .build()
        return httpsUrl.toString()
    }

    fun getCachedThumbnail(rawUrl: String): String? {
        val normalized = normalizeTikTokUrl(rawUrl) ?: return null
        return cache[normalized]?.thumbnailUrl
    }

    suspend fun resolveMetadata(
        rawUrl: String,
        forceRefresh: Boolean = false
    ): TikTokPreviewMetadata? = withContext(Dispatchers.IO) {
        val normalized = normalizeTikTokUrl(rawUrl) ?: return@withContext null

        if (!forceRefresh) {
            cache[normalized]?.let { return@withContext it }
            val lastFailure = failedTimestamps[normalized]
            if (lastFailure != null && (System.currentTimeMillis() - lastFailure) < FAILURE_COOLDOWN_MS) {
                return@withContext null
            }
        } else {
            failedTimestamps.remove(normalized)
        }

        val parsed = normalized.toHttpUrlOrNull() ?: return@withContext null
        val canonicalUrl = if (isShortTikTokUrl(parsed)) {
            expandShortTikTokUrl(normalized) ?: normalized
        } else {
            stripTrackingQuery(parsed)
        }

        val candidatesToQuery = linkedSetOf(canonicalUrl, normalized)
        for (targetUrl in candidatesToQuery) {
            val fromOEmbed = fetchOEmbed(targetUrl)
            if (fromOEmbed != null) {
                cache[normalized] = fromOEmbed
                cache[canonicalUrl] = fromOEmbed
                failedTimestamps.remove(normalized)
                return@withContext fromOEmbed
            }
        }

        val fromHtml = fetchOpenGraphFallback(canonicalUrl)
        if (fromHtml != null) {
            cache[normalized] = fromHtml
            cache[canonicalUrl] = fromHtml
            failedTimestamps.remove(normalized)
            return@withContext fromHtml
        }

        failedTimestamps[normalized] = System.currentTimeMillis()
        null
    }

    internal fun isShortTikTokUrl(url: HttpUrl): Boolean {
        val host = url.host.lowercase(Locale.US)
        val path = url.encodedPath
        return host.startsWith("vm.") ||
            host.startsWith("vt.") ||
            path.startsWith("/t/") ||
            (host == "www.tiktok.com" && path.matches(Regex("""^/[A-Za-z0-9]{6,16}/?$""")))
    }

    internal fun stripTrackingQuery(url: HttpUrl): String {
        val path = url.encodedPath
        return if (path.contains("/video/") || path.contains("/photo/")) {
            url.newBuilder()
                .scheme("https")
                .query(null)
                .fragment(null)
                .build()
                .toString()
        } else {
            url.toString()
        }
    }

    private fun expandShortTikTokUrl(shortUrl: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url(shortUrl)
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url
                if (!isTikTokHost(finalUrl.host)) return@use null
                stripTrackingQuery(finalUrl)
            }
        }.getOrNull()
    }

    private fun fetchOEmbed(targetUrl: String): TikTokPreviewMetadata? {
        val validatedTarget = targetUrl.toHttpUrlOrNull() ?: return null
        if (!isTikTokHost(validatedTarget.host)) return null

        val encoded = URLEncoder.encode(targetUrl, "UTF-8")
        val endpoint = "https://www.tiktok.com/oembed?url=$encoded"

        return runCatching {
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                parseOEmbedJson(body, canonicalUrl = targetUrl)
            }
        }.getOrNull()
    }

    private fun fetchOpenGraphFallback(targetUrl: String): TikTokPreviewMetadata? {
        val validatedTarget = targetUrl.toHttpUrlOrNull() ?: return null
        if (!isTikTokHost(validatedTarget.host)) return null

        return runCatching {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                if (!isTikTokHost(response.request.url.host)) return@use null
                val source = response.body?.source() ?: return@use null
                source.request(MAX_HTML_BYTES.toLong())
                val bytesToRead = minOf(source.buffer.size, MAX_HTML_BYTES.toLong())
                val html = source.buffer.clone().readUtf8(bytesToRead)
                extractCoverFromHtml(html, canonicalUrl = stripTrackingQuery(response.request.url))
            }
        }.getOrNull()
    }

    internal fun parseOEmbedJson(json: String, canonicalUrl: String = ""): TikTokPreviewMetadata? {
        val rawThumb = extractJsonStringField(json, "thumbnail_url") ?: return null
        val safeThumb = sanitizeThumbnailUrl(rawThumb) ?: return null
        val title = extractJsonStringField(json, "title").orEmpty().trim()
        val author = extractJsonStringField(json, "author_name").orEmpty().trim()
        return TikTokPreviewMetadata(
            thumbnailUrl = safeThumb,
            title = title,
            authorName = author,
            canonicalUrl = canonicalUrl
        )
    }

    internal fun extractCoverFromHtml(html: String, canonicalUrl: String = ""): TikTokPreviewMetadata? {
        if (html.isBlank()) return null
        for (pattern in ogImagePatterns) {
            val match = pattern.find(html)?.groupValues?.getOrNull(1) ?: continue
            val decoded = decodeEscapedString(match)
                .replace("&amp;", "&")
                .trim()
            val safeThumb = sanitizeThumbnailUrl(decoded)
            if (safeThumb != null) {
                return TikTokPreviewMetadata(
                    thumbnailUrl = safeThumb,
                    canonicalUrl = canonicalUrl
                )
            }
        }
        return null
    }

    internal fun sanitizeThumbnailUrl(rawUrl: String): String? {
        val cleaned = rawUrl.trim().replace("&amp;", "&")
        if (cleaned.isEmpty() || cleaned.length > 4000) return null
        val parsed = cleaned.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "https" && parsed.scheme != "http") return null
        val host = parsed.host.lowercase(Locale.US)
        if (host.isBlank() || host == "localhost" || host.endsWith(".local") || host.startsWith("127.") || host.startsWith("10.")) {
            return null
        }
        return parsed.newBuilder().scheme("https").build().toString()
    }

    internal fun extractJsonStringField(json: String, key: String): String? {
        val keyPattern = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val rawValue = keyPattern.find(json)?.groupValues?.getOrNull(1) ?: return null
        return decodeEscapedString(rawValue)
    }

    private fun decodeEscapedString(value: String): String {
        if (!value.contains('\\')) return value
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        if (i + 5 < value.length) {
                            val hex = value.substring(i + 2, i + 6)
                            val codePoint = hex.toIntOrNull(16)
                            if (codePoint != null) {
                                sb.append(codePoint.toChar())
                                i += 6
                            } else {
                                sb.append(c)
                                i += 1
                            }
                        } else {
                            sb.append(c)
                            i += 1
                        }
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(c)
                i += 1
            }
        }
        return sb.toString()
    }
}
