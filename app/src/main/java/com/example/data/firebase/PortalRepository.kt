package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.model.AdminAccessConfig
import com.example.data.model.CustomerInquiry
import com.example.data.model.LoginSecurityAlert
import com.example.data.model.PageVisitRecord
import com.example.data.model.ShowroomConfig
import com.example.data.model.TikTokPost
import com.example.data.model.TikTokPostDraft
import com.example.data.model.WebsiteVisitorStats
import com.example.data.network.CloudflareApiClient
import com.example.data.network.CloudflareApiException
import com.example.data.network.ShowroomLoadState
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed interface InquiriesState {
    data object Loading : InquiriesState
    data object OwnerOnly : InquiriesState
    data class Success(val inquiries: List<CustomerInquiry>) : InquiriesState
    data class Error(val message: String) : InquiriesState
}

sealed interface AdminAccessState {
    data object Loading : AdminAccessState
    data object PermissionDenied : AdminAccessState
    data class Success(val config: AdminAccessConfig) : AdminAccessState
    data class Error(val message: String) : AdminAccessState
}

class PortalRepository(
    context: Context,
    private val apiClient: CloudflareApiClient,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val firestore: FirebaseFirestore by lazy { FirestoreProvider.get(appContext) }

    private val _inquiriesState = MutableStateFlow<InquiriesState>(InquiriesState.Loading)
    private val _visitorStats = MutableStateFlow(WebsiteVisitorStats())
    private val _adminAccessState = MutableStateFlow<AdminAccessState>(loadLocalAdminAccessState())

    fun isBootstrapOwner(): Boolean {
        val email = auth.currentUser?.email ?: return false
        return email.trim().equals(AdminAccessConfig.BOOTSTRAP_OWNER_EMAIL, ignoreCase = true)
    }

    private fun requireSignedIn() {
        if (auth.currentUser == null) {
            throw IllegalStateException("Please sign in before saving changes.")
        }
    }

    suspend fun refreshShowroomData(): Result<Unit> {
        return apiClient.ensureShowroomLoaded(forceRefresh = true).map { }
    }

    fun observeShowroomConfig(): Flow<ShowroomConfig> = flow {
        apiClient.ensureShowroomLoaded(forceRefresh = false)
        emitAll(
            apiClient.showroomState.transform { state ->
                when (state) {
                    is ShowroomLoadState.Loaded -> emit(state.payload.config)
                    is ShowroomLoadState.Error -> emit(ShowroomConfig())
                    ShowroomLoadState.Loading -> Unit
                }
            }
        )
    }

    suspend fun saveShowroomConfig(config: ShowroomConfig): Result<Unit> {
        return try {
            requireSignedIn()
            apiClient.putConfig(config = config, payloadJson = config.toApiJson())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendlyException(e))
        }
    }

    fun observeTikTokPosts(): Flow<List<TikTokPost>> = flow {
        apiClient.ensureShowroomLoaded(forceRefresh = false)
        emitAll(
            apiClient.showroomState.transform { state ->
                when (state) {
                    is ShowroomLoadState.Loaded -> emit(state.payload.tiktokPosts)
                    is ShowroomLoadState.Error -> emit(emptyList())
                    ShowroomLoadState.Loading -> Unit
                }
            }
        )
    }

    suspend fun addTikTokPost(draft: TikTokPostDraft): Result<String> {
        return try {
            requireSignedIn()
            val postId = generatePostId(draft.title)
            apiClient.putTikTokPost(postId, draft.toApiJson(postId))
            Result.success(postId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendlyException(e))
        }
    }

    suspend fun updateTikTokPost(postId: String, draft: TikTokPostDraft): Result<Unit> {
        return try {
            requireSignedIn()
            require(postId.isNotBlank()) { "Post ID must not be blank." }
            apiClient.putTikTokPost(postId, draft.toApiJson(postId))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendlyException(e))
        }
    }

    suspend fun deleteTikTokPost(postId: String): Result<Unit> {
        return try {
            requireSignedIn()
            require(postId.isNotBlank()) { "Post ID must not be blank." }
            apiClient.deleteTikTokPost(postId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendlyException(e))
        }
    }

    fun observeInquiries(): Flow<InquiriesState> = flow {
        if (auth.currentUser == null) {
            emit(InquiriesState.OwnerOnly)
            return@flow
        }
        if (_inquiriesState.value is InquiriesState.Loading) {
            refreshInquiries()
        }
        emitAll(_inquiriesState)
    }

    suspend fun refreshInquiries(): Result<List<CustomerInquiry>> {
        if (auth.currentUser == null) {
            _inquiriesState.value = InquiriesState.OwnerOnly
            return Result.failure(IllegalStateException("Sign in required."))
        }
        return try {
            val inquiries = apiClient.fetchInquiries()
            _inquiriesState.value = InquiriesState.Success(inquiries)
            Result.success(inquiries)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudflareApiException) {
            if (e.isAuthOrPermissionError) {
                _inquiriesState.value = InquiriesState.OwnerOnly
            } else {
                _inquiriesState.value = InquiriesState.Error(e.message ?: "Failed to load inquiries")
            }
            Result.failure(friendlyException(e))
        } catch (e: Exception) {
            _inquiriesState.value = InquiriesState.Error(e.localizedMessage ?: "Failed to load inquiries")
            Result.failure(friendlyException(e))
        }
    }

    fun observeWebsiteVisitorStats(): Flow<WebsiteVisitorStats> = flow {
        if (auth.currentUser == null) {
            emit(WebsiteVisitorStats(isLoaded = true))
            return@flow
        }
        if (!_visitorStats.value.isLoaded) {
            refreshWebsiteVisitorStats()
        }
        emitAll(_visitorStats)
    }

    /**
     * Fetches website visitor analytics from the Cloudflare Worker API AND merges on-demand
     * single-shot Firestore `siteStats/visitors` + bounded recent `pageVisits` (never attaching a
     * continuous Firestore listener), so visitor counts work seamlessly regardless of which
     * endpoint the website reports visits to.
     */
    suspend fun refreshWebsiteVisitorStats(): Result<WebsiteVisitorStats> {
        if (auth.currentUser == null) {
            val empty = WebsiteVisitorStats(isLoaded = true)
            _visitorStats.value = empty
            return Result.success(empty)
        }
        return try {
            val workerStats = runCatching { apiClient.fetchAnalytics() }.getOrNull()
            val firestoreStats = runCatching { fetchFirestoreVisitorStatsOnDemand() }.getOrNull()

            val merged = mergeVisitorStats(workerStats, firestoreStats)
            _visitorStats.value = merged
            // Keep inquiries fresh alongside analytics when refreshing Interactions
            runCatching { refreshInquiries() }
            Result.success(merged)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!_visitorStats.value.isLoaded) {
                _visitorStats.value = WebsiteVisitorStats(isLoaded = true)
            }
            Result.failure(friendlyException(e))
        }
    }

    private suspend fun fetchFirestoreVisitorStatsOnDemand(): WebsiteVisitorStats? {
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

        val statsDoc = runCatching {
            firestore.collection(COLLECTION_SITE_STATS)
                .document(DOC_VISITORS)
                .get(Source.SERVER)
                .await()
        }.getOrElse {
            runCatching {
                firestore.collection(COLLECTION_SITE_STATS)
                    .document(DOC_VISITORS)
                    .get()
                    .await()
            }.getOrNull()
        }

        val visitDocs = runCatching {
            firestore.collection(COLLECTION_PAGE_VISITS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(MAX_ON_DEMAND_VISITS)
                .get(Source.SERVER)
                .await()
                .documents
        }.getOrElse {
            runCatching {
                firestore.collection(COLLECTION_PAGE_VISITS)
                    .limit(MAX_ON_DEMAND_VISITS)
                    .get()
                    .await()
                    .documents
            }.getOrDefault(emptyList())
        }

        if ((statsDoc == null || !statsDoc.exists()) && visitDocs.isEmpty()) {
            return null
        }

        var todayFromDocs = 0L
        var sevenDayFromDocs = 0L
        var latestMillis = 0L
        val uniqueIds = HashSet<String>()
        val records = ArrayList<PageVisitRecord>(visitDocs.size)

        visitDocs.forEach { doc ->
            val rec = parseFirestorePageVisit(doc)
            if (rec != null) {
                records.add(rec)
                if (rec.createdAtMillis > latestMillis) latestMillis = rec.createdAtMillis
                if (rec.dateKey == todayKey || (rec.createdAtMillis >= startOfTodayMillis && rec.createdAtMillis > 0L)) {
                    todayFromDocs += 1L
                }
                if (rec.createdAtMillis >= sevenDaysAgoMillis && rec.createdAtMillis > 0L) {
                    sevenDayFromDocs += 1L
                }
                uniqueIds.add(rec.visitorId.ifBlank { rec.id })
            }
        }
        records.sortByDescending { it.createdAtMillis }

        val docTotal = (statsDoc?.getLong("totalVisits") ?: 0L).coerceAtLeast(0L)
        val docUnique = (statsDoc?.getLong("uniqueVisitors") ?: 0L).coerceAtLeast(0L)
        val docTodayKey = statsDoc?.getString("todayKey").orEmpty().trim()
        val docTodayRaw = (statsDoc?.getLong("todayVisits") ?: 0L).coerceAtLeast(0L)
        val docToday = if (docTodayKey.isEmpty() || docTodayKey == todayKey) docTodayRaw else 0L
        val docLastVisit = statsDoc?.getTimestamp("lastVisitAt")?.toDate()?.time
            ?: statsDoc?.getTimestamp("updatedAt")?.toDate()?.time
            ?: 0L

        val totalVisits = maxOf(docTotal, records.size.toLong())
        val todayVisits = maxOf(docToday, todayFromDocs).coerceAtMost(totalVisits)
        val last7Days = maxOf(sevenDayFromDocs, todayVisits).coerceAtMost(totalVisits)
        val uniqueVisitors = maxOf(docUnique, uniqueIds.size.toLong()).coerceAtMost(totalVisits)

        return WebsiteVisitorStats(
            totalVisits = totalVisits,
            todayVisits = todayVisits,
            last7DaysVisits = last7Days,
            uniqueVisitors = uniqueVisitors,
            lastVisitAtMillis = maxOf(latestMillis, docLastVisit),
            recentVisitRecords = records,
            isLoaded = true
        )
    }

    private fun parseFirestorePageVisit(doc: DocumentSnapshot): PageVisitRecord? {
        val rawPage = listOf("page", "path", "pathname", "sourcePage", "url", "href")
            .firstNotNullOfOrNull { key -> doc.getString(key)?.trim()?.takeIf { it.isNotEmpty() } }
            ?: "/"
        val normalizedPage = LoginSecurityAlert.normalizePagePath(rawPage).ifBlank { "/" }
        val createdMillis = extractFirestoreTimestampMillis(doc)
        val dateKey = doc.getString("dateKey").orEmpty().trim()
        val visitorId = listOf("visitorId", "sessionId", "deviceId")
            .firstNotNullOfOrNull { key -> doc.getString(key)?.trim()?.takeIf { it.isNotEmpty() } }
            .orEmpty()
        return PageVisitRecord(
            id = doc.id,
            page = normalizedPage,
            visitorId = visitorId,
            dateKey = dateKey,
            createdAtMillis = createdMillis
        )
    }

    private fun extractFirestoreTimestampMillis(doc: DocumentSnapshot): Long {
        doc.getLong("timestampMillis")?.takeIf { it > 0L }?.let { return it }
        listOf("createdAt", "timestamp", "visitedAt", "updatedAt").forEach { key ->
            val raw = doc.get(key)
            when (raw) {
                is Timestamp -> return raw.toDate().time
                is Number -> {
                    val v = raw.toLong()
                    if (v > 0L) return if (v < 10_000_000_000L) v * 1000L else v
                }
                is String -> CloudflareApiClient.parseMillis(raw)?.let { return it }
            }
        }
        return 0L
    }

    private fun mergeVisitorStats(
        primary: WebsiteVisitorStats?,
        secondary: WebsiteVisitorStats?
    ): WebsiteVisitorStats {
        if (primary == null && secondary == null) return WebsiteVisitorStats(isLoaded = true)
        if (primary == null) return secondary!!
        if (secondary == null) return primary

        val mergedRecordsById = LinkedHashMap<String, PageVisitRecord>()
        (primary.recentVisitRecords + secondary.recentVisitRecords).forEach { rec ->
            val key = rec.id.ifBlank { "${rec.page}_${rec.createdAtMillis}_${rec.visitorId}" }
            if (!mergedRecordsById.containsKey(key)) {
                mergedRecordsById[key] = rec
            }
        }
        val mergedRecords = mergedRecordsById.values
            .sortedByDescending { it.createdAtMillis }
            .take(300)

        val totalVisits = maxOf(primary.totalVisits, secondary.totalVisits, mergedRecords.size.toLong())
        val todayVisits = maxOf(primary.todayVisits, secondary.todayVisits).coerceAtMost(totalVisits)
        val last7Days = maxOf(primary.last7DaysVisits, secondary.last7DaysVisits, todayVisits).coerceAtMost(totalVisits)
        val uniqueVisitors = maxOf(primary.uniqueVisitors, secondary.uniqueVisitors).coerceAtMost(totalVisits)
        val lastVisitAt = maxOf(primary.lastVisitAtMillis, secondary.lastVisitAtMillis)

        return WebsiteVisitorStats(
            totalVisits = totalVisits,
            todayVisits = todayVisits,
            last7DaysVisits = last7Days,
            uniqueVisitors = uniqueVisitors,
            lastVisitAtMillis = lastVisitAt,
            recentVisitRecords = mergedRecords,
            isLoaded = true
        )
    }

    fun observeAdminAccess(): Flow<AdminAccessState> = callbackFlow {
        if (auth.currentUser == null) {
            trySend(AdminAccessState.PermissionDenied)
            close()
            return@callbackFlow
        }
        trySend(loadLocalAdminAccessState())

        val reg = firestore.collection(COLLECTION_CONFIG)
            .document(DOC_ADMIN_ACCESS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "config/adminAccess listener error", error)
                    trySend(loadLocalAdminAccessState())
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val emails = (snapshot.get("adminEmails") as? List<*>)
                        ?.mapNotNull { it as? String }
                        .orEmpty()
                    val normalized = AdminAccessConfig.normalizeEmails(emails)
                    val updatedAt = snapshot.getTimestamp("updatedAt")
                    persistLocalAdminEmails(normalized, updatedAt?.toDate()?.time)
                    trySend(
                        AdminAccessState.Success(
                            AdminAccessConfig(
                                adminEmails = normalized,
                                updatedAt = updatedAt
                            )
                        )
                    )
                } else {
                    trySend(loadLocalAdminAccessState())
                }
            }

        awaitClose {
            runCatching { reg.remove() }
        }
    }

    suspend fun saveAdminEmails(emails: List<String>): Result<Unit> {
        return try {
            requireSignedIn()
            val normalized = AdminAccessConfig.normalizeEmails(emails)
            firestore.collection(COLLECTION_CONFIG)
                .document(DOC_ADMIN_ACCESS)
                .set(
                    mapOf(
                        "adminEmails" to normalized,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            persistLocalAdminEmails(normalized)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendlyException(e))
        }
    }

    suspend fun grantAdminAccessEmail(rawEmail: String): Result<Boolean> {
        return try {
            requireSignedIn()
            val existing = fetchLatestAdminEmails()
            when (val merge = AdminAccessConfig.mergeGrantedEmail(existing, rawEmail)) {
                is AdminAccessConfig.GrantEmailMergeResult.Invalid ->
                    Result.failure(IllegalArgumentException(merge.reason))
                is AdminAccessConfig.GrantEmailMergeResult.LimitReached ->
                    Result.failure(
                        IllegalStateException(
                            "Admin allowlist limit (${merge.maxCount}) reached. Remove an old admin in Settings -> Admin Access first."
                        )
                    )
                is AdminAccessConfig.GrantEmailMergeResult.AlreadyAuthorized ->
                    Result.success(false)
                is AdminAccessConfig.GrantEmailMergeResult.Added -> {
                    firestore.collection(COLLECTION_CONFIG)
                        .document(DOC_ADMIN_ACCESS)
                        .set(
                            mapOf(
                                "adminEmails" to merge.updatedEmails,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        .await()
                    persistLocalAdminEmails(merge.updatedEmails)
                    Result.success(true)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendlyException(e))
        }
    }

    suspend fun revokeAdminAccessEmail(rawEmail: String): Result<Boolean> {
        return try {
            requireSignedIn()
            val target = rawEmail.trim().lowercase(Locale.US)
            if (target == AdminAccessConfig.BOOTSTRAP_OWNER_EMAIL) {
                return Result.failure(
                    IllegalArgumentException("The primary owner account cannot be revoked.")
                )
            }
            val existing = AdminAccessConfig.normalizeEmails(fetchLatestAdminEmails())
            if (target !in existing) {
                return Result.success(false)
            }
            val updated = existing.filterNot { it == target }
            firestore.collection(COLLECTION_CONFIG)
                .document(DOC_ADMIN_ACCESS)
                .set(
                    mapOf(
                        "adminEmails" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            persistLocalAdminEmails(updated)
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendlyException(e))
        }
    }

    private suspend fun fetchLatestAdminEmails(): List<String> {
        val cloudDoc = runCatching {
            firestore.collection(COLLECTION_CONFIG)
                .document(DOC_ADMIN_ACCESS)
                .get(Source.SERVER)
                .await()
        }.getOrNull()
        if (cloudDoc != null && cloudDoc.exists()) {
            val emails = (cloudDoc.get("adminEmails") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()
            return AdminAccessConfig.normalizeEmails(emails)
        }
        return loadStoredAdminEmails()
    }

    private fun loadStoredAdminEmails(): List<String> {
        val raw = prefs.getString(KEY_ADMIN_EMAILS_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val email = arr.optString(i).trim()
                    if (email.isNotEmpty()) add(email)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadLocalAdminAccessState(): AdminAccessState {
        val emails = AdminAccessConfig.normalizeEmails(loadStoredAdminEmails())
        val updatedMillis = prefs.getLong(KEY_ADMIN_EMAILS_UPDATED_AT, 0L)
        val updatedAt = updatedMillis.takeIf { it > 0L }?.let { Timestamp(Date(it)) }
        return AdminAccessState.Success(
            AdminAccessConfig(
                adminEmails = emails,
                updatedAt = updatedAt
            )
        )
    }

    private fun persistLocalAdminEmails(emails: List<String>, updatedAtMillis: Long? = null) {
        val now = updatedAtMillis ?: System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_ADMIN_EMAILS_JSON, JSONArray(emails).toString())
            .putLong(KEY_ADMIN_EMAILS_UPDATED_AT, now)
            .apply()
        _adminAccessState.value = AdminAccessState.Success(
            AdminAccessConfig(
                adminEmails = emails,
                updatedAt = Timestamp(Date(now))
            )
        )
    }

    private fun friendlyException(e: Exception): Exception {
        return if (e is CloudflareApiException && e.isAuthOrPermissionError) {
            IllegalStateException(
                "Admin access required: sign in with an authorized admin account."
            )
        } else {
            e
        }
    }

    private fun generatePostId(title: String): String {
        val slug = title
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(36)
            .ifEmpty { "clip" }
        val suffix = UUID.randomUUID().toString().replace("-", "").take(6)
        return "tt_${slug}_$suffix"
    }

    private companion object {
        const val TAG = "PortalRepository"
        const val PREFS_NAME = "alif_portal_prefs"
        const val KEY_ADMIN_EMAILS_JSON = "admin_emails_json"
        const val KEY_ADMIN_EMAILS_UPDATED_AT = "admin_emails_updated_at"
        const val COLLECTION_CONFIG = "config"
        const val DOC_ADMIN_ACCESS = "adminAccess"
        const val COLLECTION_SITE_STATS = "siteStats"
        const val DOC_VISITORS = "visitors"
        const val COLLECTION_PAGE_VISITS = "pageVisits"
        const val MAX_ON_DEMAND_VISITS = 150L
    }
}
