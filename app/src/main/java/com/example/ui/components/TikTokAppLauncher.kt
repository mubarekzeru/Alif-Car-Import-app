package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.network.TikTokThumbnailResolver

/**
 * Launches TikTok video or profile links directly in the installed TikTok Android app,
 * falling back cleanly to the default browser when TikTok is not installed.
 */
object TikTokAppLauncher {
    val TIKTOK_PACKAGES: List<String> = listOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.zhiliaoapp.musically.go",
        "com.zhiliaoapp.musically.lite"
    )

    fun resolveLaunchUrl(
        videoUrl: String?,
        fallbackProfileUrl: String? = null,
        fallbackHandle: String? = null
    ): String? {
        normalizeCandidate(videoUrl)?.let { return it }
        normalizeCandidate(fallbackProfileUrl)?.let { return it }
        val cleanHandle = fallbackHandle.orEmpty().trim().removePrefix("@")
        if (cleanHandle.isNotEmpty() && !cleanHandle.contains(" ")) {
            return "https://www.tiktok.com/@$cleanHandle"
        }
        return null
    }

    private fun normalizeCandidate(raw: String?): String? {
        val trimmed = raw.orEmpty().trim()
        if (trimmed.isEmpty()) return null
        TikTokThumbnailResolver.normalizeTikTokUrl(trimmed)?.let { return it }
        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.contains("tiktok.com", ignoreCase = true) -> "https://$trimmed"
            else -> return null
        }
        return withScheme.takeIf { it.length <= 600 }
    }

    fun openInTikTokApp(
        context: Context,
        videoUrl: String?,
        fallbackProfileUrl: String? = null,
        fallbackHandle: String? = null
    ): Boolean {
        val targetUrl = resolveLaunchUrl(
            videoUrl = videoUrl,
            fallbackProfileUrl = fallbackProfileUrl,
            fallbackHandle = fallbackHandle
        ) ?: return false

        val uri = Uri.parse(targetUrl)
        val pm = context.packageManager

        // 1. Prefer explicitly targeting any installed TikTok app package so Android
        // launches TikTok directly instead of opening a web browser tab.
        for (pkg in TIKTOK_PACKAGES) {
            val directIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val canResolve = runCatching { directIntent.resolveActivity(pm) != null }.getOrDefault(false)
            if (canResolve) {
                val launched = runCatching { context.startActivity(directIntent) }.isSuccess
                if (launched) return true
            }
        }

        // 2. Fallback: try direct launch without pre-check (in case OEM hides resolveActivity)
        for (pkg in TIKTOK_PACKAGES) {
            val directIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(directIntent) }.isSuccess) {
                return true
            }
        }

        // 3. Final fallback: open via Android default handler / browser
        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(browserIntent) }.isSuccess
    }
}
