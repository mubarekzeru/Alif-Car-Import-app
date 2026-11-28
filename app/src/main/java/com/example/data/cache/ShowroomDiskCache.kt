package com.example.data.cache

import android.content.Context
import android.util.Log
import com.example.data.model.VehicleDraft
import com.example.data.network.CloudflareApiClient
import com.example.data.network.ShowroomPayload
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Durable on-device JSON cache for showroom vehicles (including full details and photo URLs),
 * TikTok showcase items, and showroom configuration.
 *
 * Stored in `filesDir` with atomic temp-file replacement so cached listings survive app restarts
 * and offline periods without risk of partial writes.
 */
class ShowroomDiskCache(context: Context) {
    private val cacheFile = File(context.applicationContext.filesDir, CACHE_FILE_NAME)
    private val tempFile = File(context.applicationContext.filesDir, "$CACHE_FILE_NAME.tmp")
    private val lock = Any()

    data class CachedShowroom(
        val payload: ShowroomPayload,
        val cachedAtMillis: Long
    )

    fun load(): CachedShowroom? = synchronized(lock) {
        if (!cacheFile.exists() || cacheFile.length() <= 0L) return null
        return runCatching {
            val raw = cacheFile.readText(Charsets.UTF_8)
            if (raw.isBlank()) return null
            val root = JSONObject(raw)
            val cachedAt = root.optLong(KEY_CACHED_AT_MILLIS, cacheFile.lastModified())
            val payload = CloudflareApiClient.parseShowroomPayload(raw)
            CachedShowroom(payload = payload, cachedAtMillis = cachedAt)
        }.onFailure { err ->
            Log.w(TAG, "Failed to read showroom disk cache: ${err.message}")
        }.getOrNull()
    }

    fun save(payload: ShowroomPayload, cachedAtMillis: Long = System.currentTimeMillis()) = synchronized(lock) {
        runCatching {
            val serialized = serializeShowroomPayload(payload, cachedAtMillis).toString()
            tempFile.writeText(serialized, Charsets.UTF_8)
            if (cacheFile.exists() && !tempFile.renameTo(cacheFile)) {
                cacheFile.writeText(serialized, Charsets.UTF_8)
                tempFile.delete()
            } else if (!cacheFile.exists()) {
                tempFile.renameTo(cacheFile)
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed to write showroom disk cache: ${err.message}")
        }
    }

    companion object {
        private const val TAG = "ShowroomDiskCache"
        private const val CACHE_FILE_NAME = "alif_showroom_cache.json"
        private const val KEY_CACHED_AT_MILLIS = "cachedAtMillis"

        fun serializeShowroomPayload(
            payload: ShowroomPayload,
            cachedAtMillis: Long = System.currentTimeMillis()
        ): JSONObject {
            val configJson = payload.config.toApiJson()
            val vehiclesArray = JSONArray()
            val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            payload.vehicles.forEach { vehicle ->
                val updatedIso = vehicle.updatedAt?.toDate()?.let { isoFormatter.format(it) }
                    ?: vehicle.createdAt.takeIf { it.isNotBlank() }
                    ?: VehicleDraft.currentIsoTimestamp()
                vehiclesArray.put(vehicle.toApiJson(nowIso = updatedIso))
            }

            val tiktokArray = JSONArray()
            payload.tiktokPosts.forEach { post ->
                tiktokArray.put(post.toApiJson())
            }

            return JSONObject().apply {
                put("vehicles", vehiclesArray)
                put("tiktokPosts", tiktokArray)
                put("branding", configJson.optJSONObject("branding") ?: JSONObject())
                put("hero", configJson.optJSONObject("hero") ?: JSONObject())
                put("tiktokHeader", configJson.optJSONObject("tiktokHeader") ?: JSONObject())
                put("contact", configJson.optJSONObject("contact") ?: JSONObject())
                val configUpdatedIso = payload.config.updatedAt?.toDate()?.let { isoFormatter.format(it) }
                    ?: isoFormatter.format(Date(cachedAtMillis))
                put("updatedAt", configUpdatedIso)
                put(KEY_CACHED_AT_MILLIS, cachedAtMillis)
            }
        }
    }
}
