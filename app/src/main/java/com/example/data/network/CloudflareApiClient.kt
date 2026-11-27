package com.example.data.network

import android.util.Log
import com.example.data.model.BrandingConfig
import com.example.data.model.ContactConfig
import com.example.data.model.CustomerInquiry
import com.example.data.model.HeroConfig
import com.example.data.model.LoginSecurityAlert
import com.example.data.model.PageVisitRecord
import com.example.data.model.ShowroomConfig
import com.example.data.model.TikTokPost
import com.example.data.model.TikTokSectionConfig
import com.example.data.model.Vehicle
import com.example.data.model.VehicleStatus
import com.example.data.model.WebsiteVisitorStats
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class CloudflareApiException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause) {
    val isAuthOrPermissionError: Boolean
        get() = statusCode == 401 || statusCode == 403
}

data class ShowroomPayload(
    val vehicles: List<Vehicle> = emptyList(),
    val tiktokPosts: List<TikTokPost> = emptyList(),
    val config: ShowroomConfig = ShowroomConfig()
)

sealed interface ShowroomLoadState {
    data object Loading : ShowroomLoadState
    data class Loaded(val payload: ShowroomPayload) : ShowroomLoadState
    data class Error(val message: String) : ShowroomLoadState
}

/**
 * HTTP client and shared reactive cache for the Alif Car Import Cloudflare Worker API.
 * Uses Firebase Authentication ID tokens (`Authorization: Bearer <token>`) on all `/api/admin/` endpoints.
 */
class CloudflareApiClient(
    private val auth: FirebaseAuth = Firebase.auth,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    private val showroomMutex = Mutex()
    private val _showroomState = MutableStateFlow<ShowroomLoadState>(ShowroomLoadState.Loading)
    val showroomState: StateFlow<ShowroomLoadState> = _showroomState.asStateFlow()

    @Volatile
    private var lastShowroomFetchMillis: Long = 0L

    fun currentVehicles(): List<Vehicle> =
        (_showroomState.value as? ShowroomLoadState.Loaded)?.payload?.vehicles.orEmpty()

    fun currentTikTokPosts(): List<TikTokPost> =
        (_showroomState.value as? ShowroomLoadState.Loaded)?.payload?.tiktokPosts.orEmpty()

    fun currentShowroomConfig(): ShowroomConfig =
        (_showroomState.value as? ShowroomLoadState.Loaded)?.payload?.config ?: ShowroomConfig()

    suspend fun ensureShowroomLoaded(forceRefresh: Boolean = false): Result<ShowroomPayload> {
        val current = _showroomState.value
        val now = System.currentTimeMillis()
        if (!forceRefresh &&
            current is ShowroomLoadState.Loaded &&
            (now - lastShowroomFetchMillis) < MIN_AUTO_REFRESH_INTERVAL_MILLIS
        ) {
            return Result.success(current.payload)
        }
        return showroomMutex.withLock {
            val latest = _showroomState.value
            val lockedNow = System.currentTimeMillis()
            if (!forceRefresh &&
                latest is ShowroomLoadState.Loaded &&
                (lockedNow - lastShowroomFetchMillis) < MIN_AUTO_REFRESH_INTERVAL_MILLIS
            ) {
                return@withLock Result.success(latest.payload)
            }
            try {
                val payload = fetchShowroom()
                _showroomState.value = ShowroomLoadState.Loaded(payload)
                lastShowroomFetchMillis = System.currentTimeMillis()
                Result.success(payload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch /api/showroom: ${e.message}")
                if (_showroomState.value !is ShowroomLoadState.Loaded) {
                    _showroomState.value = ShowroomLoadState.Error(
                        e.localizedMessage ?: "Failed to load showroom data from server."
                    )
                }
                Result.failure(e)
            }
        }
    }

    suspend fun fetchShowroom(): ShowroomPayload {
        val url = buildUrl("api", "showroom")
        val rawBody = executeRequest(
            method = "GET",
            url = url,
            bodyJson = null,
            requiresAdminAuth = false
        )
        return parseShowroomPayload(rawBody)
    }

    suspend fun putVehicle(vehicleId: String, payloadJson: JSONObject): Vehicle? {
        require(vehicleId.isNotBlank() && vehicleId.length <= 160) { "Invalid vehicle ID." }
        val url = buildUrl("api", "admin", "vehicles", vehicleId.trim())
        val rawBody = executeRequest(
            method = "PUT",
            url = url,
            bodyJson = payloadJson,
            requiresAdminAuth = true
        )
        val parsed = parseSingleVehicleResponse(rawBody, fallbackId = vehicleId.trim())
        if (parsed != null) {
            upsertCachedVehicle(parsed)
        }
        // Sync authoritative state from Worker (including R2 HTTPS URLs)
        runCatching { ensureShowroomLoaded(forceRefresh = true) }
        return parsed
    }

    suspend fun deleteVehicle(vehicleId: String) {
        require(vehicleId.isNotBlank() && vehicleId.length <= 160) { "Invalid vehicle ID." }
        val url = buildUrl("api", "admin", "vehicles", vehicleId.trim())
        executeRequest(
            method = "DELETE",
            url = url,
            bodyJson = null,
            requiresAdminAuth = true
        )
        removeCachedVehicle(vehicleId.trim())
    }

    suspend fun putTikTokPost(postId: String, payloadJson: JSONObject): TikTokPost? {
        require(postId.isNotBlank() && postId.length <= 160) { "Invalid TikTok post ID." }
        val url = buildUrl("api", "admin", "tiktok", postId.trim())
        val rawBody = executeRequest(
            method = "PUT",
            url = url,
            bodyJson = payloadJson,
            requiresAdminAuth = true
        )
        val parsed = parseSingleTikTokResponse(rawBody, fallbackId = postId.trim())
        if (parsed != null) {
            upsertCachedTikTokPost(parsed)
        }
        runCatching { ensureShowroomLoaded(forceRefresh = true) }
        return parsed
    }

    suspend fun deleteTikTokPost(postId: String) {
        require(postId.isNotBlank() && postId.length <= 160) { "Invalid TikTok post ID." }
        val url = buildUrl("api", "admin", "tiktok", postId.trim())
        executeRequest(
            method = "DELETE",
            url = url,
            bodyJson = null,
            requiresAdminAuth = true
        )
        removeCachedTikTokPost(postId.trim())
    }

    suspend fun putConfig(config: ShowroomConfig, payloadJson: JSONObject) {
        val url = buildUrl("api", "admin", "config")
        executeRequest(
            method = "PUT",
            url = url,
            bodyJson = payloadJson,
            requiresAdminAuth = true
        )
        updateCachedConfig(config.copy(updatedAt = Timestamp.now()))
        runCatching { ensureShowroomLoaded(forceRefresh = true) }
    }

    suspend fun fetchInquiries(): List<CustomerInquiry> {
        val url = buildUrl("api", "admin", "inquiries")
        val rawBody = executeRequest(
            method = "GET",
            url = url,
            bodyJson = null,
            requiresAdminAuth = true
        )
        return parseInquiriesResponse(rawBody)
    }

    suspend fun fetchAnalytics(): WebsiteVisitorStats {
        val url = buildUrl("api", "admin", "analytics")
        val rawBody = executeRequest(
            method = "GET",
            url = url,
            bodyJson = null,
            requiresAdminAuth = true
        )
        return parseAnalyticsResponse(rawBody)
    }

    /**
     * Verifies whether the currently signed-in Firebase user is authorized by the Cloudflare Worker
     * for `/api/admin/` endpoints.
     */
    suspend fun verifyAdminAccess(): Result<Boolean> {
        return try {
            val url = buildUrl("api", "admin", "analytics")
            executeRequest(
                method = "GET",
                url = url,
                bodyJson = null,
                requiresAdminAuth = true
            )
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudflareApiException) {
            if (e.isAuthOrPermissionError) {
                Result.success(false)
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun upsertCachedVehicle(vehicle: Vehicle) {
        val current = _showroomState.value
        if (current is ShowroomLoadState.Loaded) {
            val updatedList = current.payload.vehicles.filterNot { it.id == vehicle.id } + vehicle
            _showroomState.value = ShowroomLoadState.Loaded(
                current.payload.copy(vehicles = updatedList)
            )
        }
    }

    private fun removeCachedVehicle(vehicleId: String) {
        val current = _showroomState.value
        if (current is ShowroomLoadState.Loaded) {
            _showroomState.value = ShowroomLoadState.Loaded(
                current.payload.copy(
                    vehicles = current.payload.vehicles.filterNot { it.id == vehicleId }
                )
            )
        }
    }

    private fun upsertCachedTikTokPost(post: TikTokPost) {
        val current = _showroomState.value
        if (current is ShowroomLoadState.Loaded) {
            val updatedList = (current.payload.tiktokPosts.filterNot { it.id == post.id } + post)
                .sortedWith(compareBy<TikTokPost> { it.sortOrder }.thenBy { it.title.lowercase(Locale.US) })
            _showroomState.value = ShowroomLoadState.Loaded(
                current.payload.copy(tiktokPosts = updatedList)
            )
        }
    }

    private fun removeCachedTikTokPost(postId: String) {
        val current = _showroomState.value
        if (current is ShowroomLoadState.Loaded) {
            _showroomState.value = ShowroomLoadState.Loaded(
                current.payload.copy(
                    tiktokPosts = current.payload.tiktokPosts.filterNot { it.id == postId }
                )
            )
        }
    }

    private fun updateCachedConfig(config: ShowroomConfig) {
        val current = _showroomState.value
        if (current is ShowroomLoadState.Loaded) {
            _showroomState.value = ShowroomLoadState.Loaded(
                current.payload.copy(config = config)
            )
        }
    }

    private fun buildUrl(vararg segments: String): HttpUrl {
        val builder = baseUrl.newBuilder()
        segments.forEach { segment ->
            builder.addPathSegment(segment)
        }
        return builder.build()
    }

    private suspend fun getFirebaseIdToken(forceRefresh: Boolean): String {
        val user = auth.currentUser
            ?: throw CloudflareApiException(
                statusCode = 401,
                message = "Please sign in before accessing admin data."
            )
        val token = try {
            user.getIdToken(forceRefresh).await().token
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw CloudflareApiException(
                statusCode = 401,
                message = "Unable to authenticate session. Please sign in again.",
                cause = e
            )
        }
        if (token.isNullOrBlank()) {
            throw CloudflareApiException(
                statusCode = 401,
                message = "Authentication token unavailable. Please sign in again."
            )
        }
        return token
    }

    private suspend fun executeRequest(
        method: String,
        url: HttpUrl,
        bodyJson: JSONObject?,
        requiresAdminAuth: Boolean
    ): String {
        val firstToken = if (requiresAdminAuth) getFirebaseIdToken(forceRefresh = false) else null
        val firstResult = executeSingleHttpCall(method, url, bodyJson, firstToken)
        if (requiresAdminAuth && firstResult.statusCode == 401) {
            // Retry once with a freshly forced Firebase ID token
            val refreshedToken = getFirebaseIdToken(forceRefresh = true)
            val retryResult = executeSingleHttpCall(method, url, bodyJson, refreshedToken)
            return unwrapHttpResult(retryResult)
        }
        return unwrapHttpResult(firstResult)
    }

    private data class RawHttpResult(
        val statusCode: Int,
        val body: String
    )

    private suspend fun executeSingleHttpCall(
        method: String,
        url: HttpUrl,
        bodyJson: JSONObject?,
        bearerToken: String?
    ): RawHttpResult = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "AlifCarImportAdmin/1.0")

        // Strictly scope Authorization header to the exact API host and /api/admin/* paths
        if (!bearerToken.isNullOrBlank() &&
            url.scheme == "https" &&
            url.host.equals(baseUrl.host, ignoreCase = true) &&
            url.encodedPath.startsWith("/api/admin/")
        ) {
            requestBuilder.header("Authorization", "Bearer $bearerToken")
        }

        val requestBody = bodyJson?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
        when (method.uppercase(Locale.US)) {
            "GET" -> requestBuilder.get()
            "PUT" -> requestBuilder.put(requestBody ?: "{}".toRequestBody(JSON_MEDIA_TYPE))
            "POST" -> requestBuilder.post(requestBody ?: "{}".toRequestBody(JSON_MEDIA_TYPE))
            "DELETE" -> if (requestBody != null) requestBuilder.delete(requestBody) else requestBuilder.delete()
            else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            RawHttpResult(statusCode = response.code, body = responseBody)
        }
    }

    private fun unwrapHttpResult(result: RawHttpResult): String {
        if (result.statusCode in 200..299) {
            return result.body
        }
        val serverMsg = extractServerErrorMessage(result.body)
        val friendly = when (result.statusCode) {
            401, 403 -> serverMsg.ifBlank {
                "Admin access required: sign in with an authorized admin account."
            }
            404 -> serverMsg.ifBlank { "Requested item was not found on the server." }
            413 -> serverMsg.ifBlank { "Upload payload is too large. Try using smaller images." }
            in 500..599 -> serverMsg.ifBlank { "Server error (${result.statusCode}). Please try again." }
            else -> serverMsg.ifBlank { "Request failed (HTTP ${result.statusCode})." }
        }
        throw CloudflareApiException(statusCode = result.statusCode, message = friendly)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://alif-car-import-api.alifcarimports.workers.dev"
        private const val TAG = "CloudflareApiClient"
        private const val MIN_AUTO_REFRESH_INTERVAL_MILLIS = 15_000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Volatile
        private var sharedInstance: CloudflareApiClient? = null

        fun getShared(auth: FirebaseAuth = Firebase.auth): CloudflareApiClient {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: CloudflareApiClient(auth = auth).also { sharedInstance = it }
            }
        }

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        private val JSON_STRING_ARRAY_ITEM_REGEX = Regex(""""((?:[^"\\]|\\.)+)"""")
        private val IMAGE_VALUE_HINT_REGEX = Regex(
            """^(?:data:image/|https?://|gs://|/|./)|(?:uploads/|images/|cars/|alifcarimport/)|\.(?:jpe?g|png|webp|gif|avif|bmp)(?:[?#].*)?$""",
            RegexOption.IGNORE_CASE
        )
        private val NON_IMAGE_FIELDS = setOf(
            "id", "title", "make", "brand", "model", "year", "price", "originalPrice",
            "mileage", "transmission", "fuelType", "fuel", "color", "exteriorColor",
            "origin", "description", "tiktokUrl", "tiktok", "videoUrl", "status",
            "featured", "createdAt", "updatedAt"
        )

        private fun extractServerErrorMessage(rawBody: String): String {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) return ""
            return runCatching {
                val obj = JSONObject(trimmed)
                listOf("error", "message", "detail", "reason")
                    .firstNotNullOfOrNull { key ->
                        obj.optString(key).trim().takeIf { it.isNotEmpty() }
                    }.orEmpty()
            }.getOrDefault("")
        }

        fun parseShowroomPayload(rawBody: String): ShowroomPayload {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) return ShowroomPayload()
            val root = JSONObject(trimmed)
            val dataRoot = root.optJSONObject("data") ?: root

            val vehiclesArr = dataRoot.optJSONArray("vehicles") ?: root.optJSONArray("vehicles")
            val vehicles = parseVehiclesArray(vehiclesArr)

            val tiktokArr = dataRoot.optJSONArray("tiktokPosts")
                ?: dataRoot.optJSONArray("tiktok")
                ?: root.optJSONArray("tiktokPosts")
            val tiktokPosts = parseTikTokPostsArray(tiktokArr)

            val config = parseShowroomConfig(dataRoot)
            return ShowroomPayload(
                vehicles = vehicles,
                tiktokPosts = tiktokPosts,
                config = config
            )
        }

        fun parseSingleVehicleResponse(rawBody: String, fallbackId: String): Vehicle? {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
            return runCatching {
                val obj = JSONObject(trimmed)
                val target = obj.optJSONObject("vehicle")
                    ?: obj.optJSONObject("data")
                    ?: obj
                if (target.has("title") || target.has("make") || target.has("price") || target.has("imageUrl")) {
                    parseVehicleObject(target, fallbackId)
                } else {
                    null
                }
            }.getOrNull()
        }

        fun parseSingleTikTokResponse(rawBody: String, fallbackId: String): TikTokPost? {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
            return runCatching {
                val obj = JSONObject(trimmed)
                val target = obj.optJSONObject("post")
                    ?: obj.optJSONObject("tiktokPost")
                    ?: obj.optJSONObject("data")
                    ?: obj
                if (target.has("title") || target.has("tiktokUrl")) {
                    parseTikTokPostObject(target, fallbackId)
                } else {
                    null
                }
            }.getOrNull()
        }

        fun parseShowroomConfig(root: JSONObject): ShowroomConfig {
            val defaults = ShowroomConfig()
            val bObj = root.optJSONObject("branding")
            val hObj = root.optJSONObject("hero")
            val tObj = root.optJSONObject("tiktokHeader") ?: root.optJSONObject("tiktok")
            val cObj = root.optJSONObject("contact")

            val branding = if (bObj != null) {
                BrandingConfig(
                    companyName = bObj.optStringClean("companyName").ifBlank { defaults.branding.companyName },
                    logoUrl = listOf("logoUrl", "logoURL", "logo_url", "logo", "imageUrl", "image")
                        .firstNotNullOfOrNull { extractPortalImageUrl(bObj.opt(it)) }
                        .orEmpty(),
                    tagline = bObj.optStringClean("tagline")
                )
            } else {
                defaults.branding
            }

            val hero = if (hObj != null) {
                HeroConfig(
                    kicker = hObj.optStringClean("kicker"),
                    title = hObj.optStringClean("title").ifBlank { defaults.hero.title },
                    description = hObj.optStringClean("description"),
                    imageUrl = listOf("imageUrl", "imageURL", "image_url", "image", "bannerUrl", "backgroundImage", "coverImage")
                        .firstNotNullOfOrNull { extractPortalImageUrl(hObj.opt(it)) }
                        .orEmpty(),
                    ctaLabel = hObj.optStringClean("ctaLabel").ifBlank { defaults.hero.ctaLabel }
                )
            } else {
                defaults.hero
            }

            val tiktok = if (tObj != null) {
                TikTokSectionConfig(
                    title = tObj.optStringClean("title").ifBlank { defaults.tiktok.title },
                    description = tObj.optStringClean("description"),
                    profileUrl = tObj.optStringClean("profileUrl"),
                    handle = tObj.optStringClean("handle")
                )
            } else {
                defaults.tiktok
            }

            val contact = if (cObj != null) {
                fun firstString(vararg keys: String): String =
                    keys.firstNotNullOfOrNull { k -> cObj.optStringClean(k).takeIf { it.isNotEmpty() } }.orEmpty()

                ContactConfig(
                    addressLine = firstString("addressLine"),
                    cityCountry = firstString("cityCountry"),
                    hours = firstString("hours"),
                    phone = firstString("phone"),
                    whatsapp = firstString("whatsapp", "whatsApp", "whatsappUrl"),
                    telegram = firstString("telegram", "telegramUrl"),
                    instagram = firstString("instagram", "instagramUrl"),
                    facebook = firstString("facebook", "facebookUrl"),
                    x = firstString("x", "twitter", "twitterUrl", "xUrl"),
                    youtube = firstString("youtube", "youtubeUrl", "youTube"),
                    snapchat = firstString("snapchat", "snapchatUrl", "snapChat"),
                    linkedin = firstString("linkedin", "linkedIn", "linkedinUrl"),
                    pinterest = firstString("pinterest", "pinterestUrl"),
                    threads = firstString("threads", "threadsUrl"),
                    reddit = firstString("reddit", "redditUrl"),
                    viber = firstString("viber", "viberUrl"),
                    imo = firstString("imo", "imoUrl"),
                    wechat = firstString("wechat", "weChat", "wechatId"),
                    discord = firstString("discord", "discordUrl"),
                    twitch = firstString("twitch", "twitchUrl"),
                    website = firstString("website", "websiteUrl", "otherLink"),
                    email = firstString("email"),
                    mapsUrl = firstString("mapsUrl"),
                    landmarkNote = firstString("landmarkNote")
                )
            } else {
                defaults.contact
            }

            val updatedAt = parseTimestamp(root.opt("updatedAt"))
            return ShowroomConfig(
                branding = branding,
                hero = hero,
                tiktok = tiktok,
                contact = contact,
                updatedAt = updatedAt
            )
        }

        private fun parseVehiclesArray(array: JSONArray?): List<Vehicle> {
            if (array == null) return emptyList()
            val result = ArrayList<Vehicle>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                parseVehicleObject(obj)?.let { result.add(it) }
            }
            return result
        }

        fun parseVehicleObject(obj: JSONObject, fallbackId: String = ""): Vehicle? {
            val id = obj.optStringClean("id").ifBlank { fallbackId }
            if (id.isBlank()) return null

            val galleryKeys = listOf(
                "galleryImages", "gallery", "galleryUrls", "images", "imageUrls",
                "photos", "photoUrls", "pictures", "media", "additionalImages",
                "extraImages", "files", "attachments"
            )
            val gallery = buildList {
                galleryKeys.forEach { key ->
                    extractImageUrls(obj.opt(key)).forEach { url ->
                        if (!url.isPlaceholderImageUrl() && url !in this) add(url)
                    }
                }
            }.toMutableList()

            val primaryKeys = listOf(
                "imageUrl", "imageURL", "image_url", "image", "img", "imgUrl", "img_url",
                "mainImage", "mainImageUrl", "primaryImage", "primaryImageUrl",
                "featuredImage", "featuredImageUrl", "coverImage", "coverImageUrl",
                "coverUrl", "cover", "thumbnailUrl", "thumbnail", "thumbUrl", "thumb",
                "photoUrl", "photoURL", "photo_url", "photo", "pictureUrl", "picture",
                "carImage", "carImageUrl", "vehicleImage", "vehicleImageUrl",
                "heroImage", "imageSrc", "imageData", "imageBase64",
                "photoData", "photoBase64", "dataUrl"
            )
            val primaryCandidates = primaryKeys.flatMap { extractImageUrls(obj.opt(it)) }

            if (primaryCandidates.none { !it.isPlaceholderImageUrl() } && gallery.isEmpty()) {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key !in NON_IMAGE_FIELDS) {
                        extractImageUrls(obj.opt(key)).forEach { candidate ->
                            if (!candidate.isPlaceholderImageUrl() &&
                                (IMAGE_VALUE_HINT_REGEX.containsMatchIn(candidate) || candidate.looksLikeRawBase64Image()) &&
                                candidate !in gallery
                            ) {
                                gallery.add(candidate)
                            }
                        }
                    }
                }
            }

            val resolvedPrimaryImage = primaryCandidates.firstOrNull { !it.isPlaceholderImageUrl() }
                ?: gallery.firstOrNull()
                ?: primaryCandidates.firstOrNull().orEmpty()

            val rawMileage = obj.opt("mileage")
            val mileageString = when (rawMileage) {
                is String -> rawMileage.trim()
                is Number -> "${rawMileage.toLong()} km"
                else -> ""
            }

            val createdAtString = obj.optStringClean("createdAt")
            val parsedYear = parseLong(obj.opt("year"), defaultValue = 2024L)
            val parsedPrice = parseLong(obj.opt("price"), defaultValue = 0L)
            val parsedOriginalPrice = parseNullableLong(obj.opt("originalPrice"))
            val parsedFeatured = parseBoolean(obj.opt("featured"))
            val parsedUpdatedAt = parseTimestamp(obj.opt("updatedAt"))
                ?: parseTimestamp(obj.opt("createdAt"))

            val parsedMake = obj.optStringClean("make").ifBlank { obj.optStringClean("brand") }
            val parsedFuelType = obj.optStringClean("fuelType").ifBlank { obj.optStringClean("fuel") }
            val parsedColor = obj.optStringClean("color").ifBlank { obj.optStringClean("exteriorColor") }

            return Vehicle(
                id = id,
                title = obj.optStringClean("title"),
                make = parsedMake,
                model = obj.optStringClean("model"),
                year = parsedYear,
                price = parsedPrice,
                originalPrice = parsedOriginalPrice,
                mileage = mileageString,
                transmission = obj.optStringClean("transmission"),
                fuelType = parsedFuelType,
                color = parsedColor,
                origin = obj.optStringClean("origin"),
                imageUrl = resolvedPrimaryImage,
                galleryImages = gallery,
                description = obj.optStringClean("description"),
                tiktokUrl = obj.optStringClean("tiktokUrl"),
                status = obj.optStringClean("status").ifBlank { VehicleStatus.AVAILABLE.value },
                featured = parsedFeatured,
                createdAt = createdAtString,
                updatedAt = parsedUpdatedAt
            )
        }

        private fun parseTikTokPostsArray(array: JSONArray?): List<TikTokPost> {
            if (array == null) return emptyList()
            val result = ArrayList<TikTokPost>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                parseTikTokPostObject(obj)?.let { result.add(it) }
            }
            return result.sortedWith(
                compareBy<TikTokPost> { it.sortOrder }.thenBy { it.title.lowercase(Locale.US) }
            )
        }

        fun parseTikTokPostObject(obj: JSONObject, fallbackId: String = ""): TikTokPost? {
            val id = obj.optStringClean("id").ifBlank { fallbackId }
            if (id.isBlank()) return null

            val resolvedCover = listOf(
                "coverImageUrl", "coverImage", "coverUrl", "cover",
                "thumbnailUrl", "thumbnail", "thumbUrl", "imageUrl", "image", "photoUrl", "photo"
            ).firstNotNullOfOrNull { extractPortalImageUrl(obj.opt(it)) }.orEmpty()

            return TikTokPost(
                id = id,
                title = obj.optStringClean("title"),
                caption = obj.optStringClean("caption"),
                tiktokUrl = obj.optStringClean("tiktokUrl"),
                coverImageUrl = resolvedCover,
                sortOrder = parseLong(obj.opt("sortOrder"), defaultValue = 0L),
                updatedAt = parseTimestamp(obj.opt("updatedAt")) ?: parseTimestamp(obj.opt("createdAt"))
            )
        }

        fun parseInquiriesResponse(rawBody: String): List<CustomerInquiry> {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) return emptyList()
            val array = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("inquiries")
                        ?: obj.optJSONArray("data")
                        ?: obj.optJSONArray("items")
                        ?: JSONArray()
                }
                else -> JSONArray()
            }
            val list = ArrayList<CustomerInquiry>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optStringClean("id").ifBlank { "inq_$i" }
                val createdAt = parseTimestamp(item.opt("createdAt"))
                    ?: parseTimestamp(item.opt("timestamp"))
                    ?: parseTimestamp(item.opt("timestampMillis"))
                list.add(
                    CustomerInquiry(
                        id = id,
                        name = item.optStringClean("name"),
                        phone = item.optStringClean("phone"),
                        email = item.optStringClean("email"),
                        vehicleTitle = item.optStringClean("vehicleTitle")
                            .ifBlank { item.optStringClean("vehicle") },
                        message = item.optStringClean("message"),
                        createdAt = createdAt
                    )
                )
            }
            return list.sortedByDescending { it.createdAt?.seconds ?: 0L }
        }

        fun parseAnalyticsResponse(rawBody: String): WebsiteVisitorStats {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) return WebsiteVisitorStats(isLoaded = true)
            val root = if (trimmed.startsWith("[")) {
                JSONObject().put("visits", JSONArray(trimmed))
            } else {
                JSONObject(trimmed)
            }
            val data = root.optJSONObject("analytics")
                ?: root.optJSONObject("stats")
                ?: root.optJSONObject("data")
                ?: root

            val nowMillis = System.currentTimeMillis()
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
            val startOfTodayMillis = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val sevenDaysAgoMillis = nowMillis - (7L * 24L * 60L * 60L * 1000L)

            val visitsArr = data.optJSONArray("recentVisitRecords")
                ?: data.optJSONArray("recentVisits")
                ?: data.optJSONArray("pageVisits")
                ?: data.optJSONArray("visits")
                ?: data.optJSONArray("records")
                ?: data.optJSONArray("events")
                ?: root.optJSONArray("visits")
                ?: root.optJSONArray("recentVisits")
                ?: root.optJSONArray("pageVisits")

            var visitsTodayFromLog = 0L
            var visits7dFromLog = 0L
            var latestVisitMillis = 0L
            val uniqueIds = HashSet<String>()
            val parsedRecords = ArrayList<PageVisitRecord>()

            if (visitsArr != null) {
                for (i in 0 until visitsArr.length()) {
                    val item = visitsArr.optJSONObject(i) ?: continue
                    val createdMillis = listOf("createdAtMillis", "timestampMillis", "visitedAtMillis", "createdAt", "timestamp", "visitedAt", "updatedAt")
                        .firstNotNullOfOrNull { key -> parseMillis(item.opt(key)) }
                        ?: parseDateKeyMillis(item.optStringClean("dateKey").ifBlank { item.optStringClean("date") })
                        ?: 0L
                    if (createdMillis > latestVisitMillis) {
                        latestVisitMillis = createdMillis
                    }
                    val docDateKey = item.optStringClean("dateKey").ifBlank { item.optStringClean("date") }
                    if (docDateKey == todayKey || (createdMillis >= startOfTodayMillis && createdMillis > 0L)) {
                        visitsTodayFromLog += 1L
                    }
                    if (createdMillis >= sevenDaysAgoMillis && createdMillis > 0L) {
                        visits7dFromLog += 1L
                    }
                    val visitorId = item.optStringClean("visitorId")
                        .ifBlank { item.optStringClean("visitor_id") }
                        .ifBlank { item.optStringClean("deviceId") }
                        .ifBlank { item.optStringClean("sessionId") }
                    val recordId = item.optStringClean("id").ifBlank { "visit_$i" }
                    uniqueIds.add(visitorId.ifBlank { recordId })

                    val rawPage = item.optStringClean("page")
                        .ifBlank { item.optStringClean("path") }
                        .ifBlank { item.optStringClean("pathname") }
                        .ifBlank { item.optStringClean("sourcePage") }
                        .ifBlank { item.optStringClean("url") }
                        .ifBlank { "/" }
                    val normalizedPage = LoginSecurityAlert.normalizePagePath(rawPage).ifBlank { "/" }
                    parsedRecords.add(
                        PageVisitRecord(
                            id = recordId,
                            page = normalizedPage,
                            visitorId = visitorId,
                            dateKey = docDateKey,
                            createdAtMillis = createdMillis
                        )
                    )
                }
            }

            // Also synthesize records from aggregated `daily` or `byDay` arrays if individual visits weren't returned
            val dailyArr = data.optJSONArray("daily")
                ?: data.optJSONArray("dailyVisits")
                ?: data.optJSONArray("byDay")
                ?: data.optJSONArray("days")
            var totalFromDaily = 0L
            var todayFromDaily = 0L
            var sevenDayFromDaily = 0L
            if (dailyArr != null) {
                for (i in 0 until dailyArr.length()) {
                    val item = dailyArr.optJSONObject(i) ?: continue
                    val count = parseLong(
                        item.opt("visits") ?: item.opt("views") ?: item.opt("count") ?: item.opt("total"),
                        defaultValue = 0L
                    ).coerceAtLeast(0L)
                    totalFromDaily += count
                    val dateKey = item.optStringClean("date").ifBlank { item.optStringClean("dateKey") }
                    val dayMillis = parseDateKeyMillis(dateKey) ?: 0L
                    if (dateKey == todayKey) {
                        todayFromDaily += count
                    }
                    if (dayMillis >= sevenDaysAgoMillis) {
                        sevenDayFromDaily += count
                    }
                    if (parsedRecords.isEmpty() && count > 0L && dayMillis > 0L) {
                        val capped = count.coerceAtMost(50L).toInt()
                        for (n in 0 until capped) {
                            parsedRecords.add(
                                PageVisitRecord(
                                    id = "day_${dateKey}_$n",
                                    page = "/",
                                    visitorId = "",
                                    dateKey = dateKey,
                                    createdAtMillis = dayMillis + (n * 1000L)
                                )
                            )
                        }
                    }
                }
            }

            parsedRecords.sortByDescending { it.createdAtMillis }

            val counterTotal = listOf("totalVisits", "totalViews", "totalPageViews", "total", "count", "views", "visits")
                .firstNotNullOfOrNull { key -> parseNullableLong(data.opt(key)) }
                ?.coerceAtLeast(0L) ?: 0L
            val counterToday = listOf("todayVisits", "todayViews", "today", "viewsToday", "visitsToday")
                .firstNotNullOfOrNull { key -> parseNullableLong(data.opt(key)) }
                ?.coerceAtLeast(0L) ?: 0L
            val counter7d = listOf("last7DaysVisits", "last7DaysViews", "last7Days", "weekVisits", "weekViews", "weeklyVisits")
                .firstNotNullOfOrNull { key -> parseNullableLong(data.opt(key)) }
                ?.coerceAtLeast(0L) ?: 0L
            val counterUnique = listOf("uniqueVisitors", "uniqueViews", "unique", "visitors")
                .firstNotNullOfOrNull { key -> parseNullableLong(data.opt(key)) }
                ?.coerceAtLeast(0L) ?: 0L
            val counterLastVisit = listOf("lastVisitAtMillis", "lastVisitAt", "updatedAt", "lastSeenAt")
                .firstNotNullOfOrNull { key -> parseMillis(data.opt(key)) } ?: 0L

            val totalVisits = maxOf(counterTotal, totalFromDaily, parsedRecords.size.toLong())
            val todayVisits = maxOf(counterToday, todayFromDaily, visitsTodayFromLog).coerceAtMost(totalVisits)
            val last7Days = maxOf(counter7d, sevenDayFromDaily, visits7dFromLog, todayVisits).coerceAtMost(totalVisits)
            val uniqueVisitors = maxOf(counterUnique, uniqueIds.size.toLong()).coerceAtMost(totalVisits)
            val lastVisitAt = maxOf(latestVisitMillis, counterLastVisit)

            return WebsiteVisitorStats(
                totalVisits = totalVisits,
                todayVisits = todayVisits,
                last7DaysVisits = last7Days,
                uniqueVisitors = uniqueVisitors,
                lastVisitAtMillis = lastVisitAt,
                recentVisitRecords = parsedRecords,
                isLoaded = true
            )
        }

        private fun JSONObject.optStringClean(key: String): String {
            if (!has(key) || isNull(key)) return ""
            val value = opt(key)
            return if (value is String) value.trim() else ""
        }

        private fun parseLong(raw: Any?, defaultValue: Long): Long =
            parseNullableLong(raw) ?: defaultValue

        private fun parseNullableLong(raw: Any?): Long? = when (raw) {
            null, JSONObject.NULL -> null
            is Number -> raw.toLong()
            is String -> raw.replace(",", "").trim().toLongOrNull()
            else -> null
        }

        private fun parseBoolean(raw: Any?): Boolean = when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw.trim().equals("true", ignoreCase = true) || raw.trim() == "1"
            else -> false
        }

        fun parseMillis(raw: Any?): Long? {
            if (raw == null || raw == JSONObject.NULL) return null
            return when (raw) {
                is Number -> {
                    val v = raw.toLong()
                    when {
                        v <= 0L -> null
                        v < 10_000_000_000L -> v * 1000L // Unix seconds -> millis
                        else -> v
                    }
                }
                is String -> {
                    val trimmed = raw.trim()
                    if (trimmed.isEmpty()) return null
                    trimmed.toLongOrNull()?.let { numeric ->
                        return if (numeric < 10_000_000_000L) numeric * 1000L else numeric
                    }
                    parseIsoTimestampMillis(trimmed)
                }
                is JSONObject -> {
                    val seconds = parseNullableLong(raw.opt("seconds") ?: raw.opt("_seconds"))
                    if (seconds != null && seconds > 0L) seconds * 1000L else null
                }
                else -> null
            }
        }

        fun parseTimestamp(raw: Any?): Timestamp? {
            val millis = parseMillis(raw) ?: return null
            return Timestamp(Date(millis))
        }

        private fun parseDateKeyMillis(dateKey: String): Long? {
            if (dateKey.length < 10) return null
            val prefix = dateKey.take(10)
            return runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }.parse(prefix)?.time
            }.getOrNull()?.takeIf { it > 0L }
        }

        private fun parseIsoTimestampMillis(raw: String): Long? {
            if (raw.length < 10) return null
            val normalized = raw.replace("Z", "+0000")
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd"
            )
            for (pattern in patterns) {
                val parsed = runCatching {
                    SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(normalized)?.time
                }.getOrNull()
                if (parsed != null && parsed > 0L) return parsed
            }
            return null
        }

        private fun extractPortalImageUrl(raw: Any?): String? = when (raw) {
            null, JSONObject.NULL -> null
            is String -> raw.trim().takeIf { it.isNotEmpty() }
            is JSONObject -> listOf("url", "imageUrl", "downloadURL", "downloadUrl", "src", "secure_url", "path")
                .firstNotNullOfOrNull { key -> raw.optStringClean(key).takeIf { it.isNotEmpty() } }
            is JSONArray -> {
                var found: String? = null
                for (i in 0 until raw.length()) {
                    val candidate = extractPortalImageUrl(raw.opt(i))
                    if (candidate != null) {
                        found = candidate
                        break
                    }
                }
                found
            }
            else -> null
        }

        private fun extractImageUrls(raw: Any?): List<String> = when (raw) {
            null, JSONObject.NULL -> emptyList()
            is String -> {
                val trimmed = raw.trim()
                when {
                    trimmed.isEmpty() -> emptyList()
                    trimmed.startsWith("data:", ignoreCase = true) -> listOf(trimmed)
                    trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                        JSON_STRING_ARRAY_ITEM_REGEX.findAll(trimmed)
                            .map { it.groupValues[1].replace("\\/", "/").trim() }
                            .filter { it.isNotEmpty() }
                            .toList()
                    }
                    trimmed.contains('\n') -> {
                        trimmed.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
                    }
                    else -> listOf(trimmed)
                }
            }
            is JSONArray -> {
                val out = mutableListOf<String>()
                for (i in 0 until raw.length()) {
                    out.addAll(extractImageUrls(raw.opt(i)))
                }
                out
            }
            is JSONObject -> {
                val candidateKeys = listOf(
                    "url", "URL", "imageUrl", "imageURL", "image_url",
                    "downloadURL", "downloadUrl", "download_url",
                    "src", "secure_url", "secureUrl", "uri", "href", "link",
                    "path", "image", "photo", "thumbnail", "thumbnailUrl",
                    "coverUrl", "preview", "data", "base64"
                )
                candidateKeys.firstNotNullOfOrNull { key ->
                    extractImageUrls(raw.opt(key)).firstOrNull()
                }?.let { listOf(it) } ?: emptyList()
            }
            else -> emptyList()
        }

        private fun String.isPlaceholderImageUrl(): Boolean {
            val t = trim().lowercase(Locale.US)
            return t.isEmpty() ||
                t == "null" ||
                t == "undefined" ||
                t == "https://alifcarimport.com/placeholder.jpg" ||
                t == "https://mubarekzeru.github.io/alifcarimport/placeholder.jpg" ||
                t == "/placeholder.jpg" ||
                t == "placeholder.jpg" ||
                t.endsWith("/placeholder.jpg") ||
                t.endsWith("/placeholder.png") ||
                t.startsWith("blob:")
        }

        private fun String.looksLikeRawBase64Image(): Boolean {
            val compact = replace("\\s+".toRegex(), "")
            return compact.length >= 64 && (
                compact.startsWith("/9j/") ||
                    compact.startsWith("iVBORw0KGgo") ||
                    compact.startsWith("UklGR") ||
                    compact.startsWith("R0lGOD")
                )
        }
    }
}
