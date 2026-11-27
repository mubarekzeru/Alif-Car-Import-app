package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AdminAccessConfig
import com.example.data.model.BrandingConfig
import com.example.data.model.ContactConfig
import com.example.data.model.CustomerInquiry
import com.example.data.model.HeroConfig
import com.example.data.model.PageVisitRecord
import com.example.data.model.ShowroomConfig
import com.example.data.model.TikTokPost
import com.example.data.model.TikTokPostDraft
import com.example.data.model.TikTokSectionConfig
import com.example.data.model.WebsiteVisitorStats
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
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
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val showroomDoc
        get() = db.collection("config").document("showroom")

    private val adminAccessDoc
        get() = db.collection("config").document("adminAccess")

    private val tikTokPostsCol
        get() = db.collection("tiktokPosts")

    private val inquiriesCol
        get() = db.collection("inquiries")

    private val pageVisitsCol
        get() = db.collection("pageVisits")

    private val visitorStatsDoc
        get() = db.collection("siteStats").document("visitors")

    fun isBootstrapOwner(): Boolean {
        val email = auth.currentUser?.email ?: return false
        return email.trim().equals(AdminAccessConfig.BOOTSTRAP_OWNER_EMAIL, ignoreCase = true)
    }

    private fun requireSignedIn() {
        if (auth.currentUser == null) {
            throw IllegalStateException("Please sign in with Google before saving changes.")
        }
    }

    fun observeShowroomConfig(): Flow<ShowroomConfig> = flow {
        val path = "config/showroom"
        emitAll(
            showroomDoc
                .snapshots()
                .map { snapshot -> snapshot.toShowroomConfigSafe() }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveShowroomConfig(config: ShowroomConfig): Result<Unit> {
        val path = "config/showroom"
        return try {
            requireSignedIn()
            showroomDoc.set(config.toFirestoreMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(friendlyException(e))
        }
    }

    fun observeTikTokPosts(): Flow<List<TikTokPost>> = flow {
        val path = "tiktokPosts"
        emitAll(
            tikTokPostsCol
                .snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { it.toTikTokPostSafe() }
                        .sortedWith(compareBy<TikTokPost> { it.sortOrder }.thenBy { it.title.lowercase(Locale.US) })
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addTikTokPost(draft: TikTokPostDraft): Result<String> {
        var docPath = "tiktokPosts"
        return try {
            requireSignedIn()
            val postId = generatePostId(draft.title)
            val docRef = tikTokPostsCol.document(postId)
            docPath = docRef.path
            docRef.set(draft.toCreateMap(postId)).await()
            Result.success(postId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(friendlyException(e))
        }
    }

    suspend fun updateTikTokPost(postId: String, draft: TikTokPostDraft): Result<Unit> {
        val docPath = "tiktokPosts/$postId"
        return try {
            requireSignedIn()
            require(postId.isNotBlank()) { "Post ID must not be blank." }
            tikTokPostsCol.document(postId).update(draft.toUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(friendlyException(e))
        }
    }

    suspend fun deleteTikTokPost(postId: String): Result<Unit> {
        val docPath = "tiktokPosts/$postId"
        return try {
            requireSignedIn()
            require(postId.isNotBlank()) { "Post ID must not be blank." }
            tikTokPostsCol.document(postId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(friendlyException(e))
        }
    }

    fun observeInquiries(): Flow<InquiriesState> = flow {
        if (!isBootstrapOwner()) {
            emit(InquiriesState.OwnerOnly)
            return@flow
        }
        val path = "inquiries"
        emitAll(
            inquiriesCol
                .snapshots()
                .map { snapshot ->
                    val list = snapshot.documents
                        .mapNotNull { it.toInquirySafe() }
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                    InquiriesState.Success(list) as InquiriesState
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        emit(InquiriesState.OwnerOnly)
                    } else {
                        val root = firestoreError ?: error
                        handleFirestoreError(root, OperationType.LIST, path)
                        emit(InquiriesState.Error(root.localizedMessage ?: "Failed to load inquiries"))
                    }
                }
        )
    }

    fun observeAdminAccess(): Flow<AdminAccessState> = flow {
        if (auth.currentUser == null) {
            emit(AdminAccessState.PermissionDenied)
            return@flow
        }
        val path = "config/adminAccess"
        emitAll(
            adminAccessDoc
                .snapshots()
                .map { snapshot ->
                    val raw = (snapshot.data?.get("adminEmails") as? List<*>)
                        ?.mapNotNull { it as? String }
                        ?: emptyList()
                    val updatedAt = runCatching {
                        snapshot.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.getOrNull()
                    AdminAccessState.Success(
                        AdminAccessConfig(
                            adminEmails = AdminAccessConfig.normalizeEmails(raw),
                            updatedAt = updatedAt
                        )
                    ) as AdminAccessState
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        emit(AdminAccessState.PermissionDenied)
                    } else {
                        val root = firestoreError ?: error
                        handleFirestoreError(root, OperationType.GET, path)
                        emit(AdminAccessState.Error(root.localizedMessage ?: "Failed to load admin access"))
                    }
                }
        )
    }

    suspend fun saveAdminEmails(emails: List<String>): Result<Unit> {
        val path = "config/adminAccess"
        return try {
            requireSignedIn()
            adminAccessDoc.set(AdminAccessConfig.toFirestoreMap(emails)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(friendlyException(e))
        }
    }

    suspend fun grantAdminAccessEmail(rawEmail: String): Result<Boolean> {
        val path = "config/adminAccess"
        return try {
            requireSignedIn()
            val snapshot = runCatching { adminAccessDoc.get(Source.SERVER).await() }
                .getOrElse { adminAccessDoc.get().await() }
            val existing = (snapshot.data?.get("adminEmails") as? List<*>)
                ?.mapNotNull { it as? String }
                ?: emptyList()
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
                    adminAccessDoc.set(AdminAccessConfig.toFirestoreMap(merge.updatedEmails)).await()
                    Result.success(true)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(friendlyException(e))
        }
    }

    suspend fun revokeAdminAccessEmail(rawEmail: String): Result<Boolean> {
        val path = "config/adminAccess"
        return try {
            requireSignedIn()
            val target = rawEmail.trim().lowercase(Locale.US)
            if (target == AdminAccessConfig.BOOTSTRAP_OWNER_EMAIL) {
                return Result.failure(
                    IllegalArgumentException("The primary owner account cannot be revoked.")
                )
            }
            val snapshot = runCatching { adminAccessDoc.get(Source.SERVER).await() }
                .getOrElse { adminAccessDoc.get().await() }
            val existing = AdminAccessConfig.normalizeEmails(
                (snapshot.data?.get("adminEmails") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: emptyList()
            )
            if (target !in existing) {
                return Result.success(false)
            }
            val updated = existing.filterNot { it == target }
            adminAccessDoc.set(AdminAccessConfig.toFirestoreMap(updated)).await()
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(friendlyException(e))
        }
    }

    fun observeWebsiteVisitorStats(): Flow<WebsiteVisitorStats> = flow {
        if (auth.currentUser == null) {
            emit(WebsiteVisitorStats(isLoaded = true))
            return@flow
        }
        val statsFlow: Flow<DocumentSnapshot?> = visitorStatsDoc
            .snapshots()
            .map<DocumentSnapshot, DocumentSnapshot?> { it }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError?.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, "siteStats/visitors")
                }
                emit(null)
            }
        val visitsFlow: Flow<List<DocumentSnapshot>> = pageVisitsCol
            .limit(2000)
            .snapshots()
            .map { it.documents }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError?.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "pageVisits")
                }
                emit(emptyList())
            }
        // Also silently recover any public website visits that were mistakenly written
        // to loginAlerts or adminLoginAttempts by older website builds so they count in Interactions.
        val misroutedAlertsFlow: Flow<List<DocumentSnapshot>> = db.collection("loginAlerts")
            .limit(300)
            .snapshots()
            .map { snap -> snap.documents.filter { isPublicVisitFallbackDoc(it) } }
            .catch { error ->
                if (unwrapFirestoreException(error) == null && error is CancellationException) throw error
                emit(emptyList())
            }
        val misroutedAttemptsFlow: Flow<List<DocumentSnapshot>> = db.collection("adminLoginAttempts")
            .limit(300)
            .snapshots()
            .map { snap -> snap.documents.filter { isPublicVisitFallbackDoc(it) } }
            .catch { error ->
                if (unwrapFirestoreException(error) == null && error is CancellationException) throw error
                emit(emptyList())
            }
        emitAll(
            combine(statsFlow, visitsFlow, misroutedAlertsFlow, misroutedAttemptsFlow) { statsSnap, visitDocs, misroutedAlerts, misroutedAttempts ->
                computeVisitorStats(statsSnap, visitDocs + misroutedAlerts + misroutedAttempts)
            }
        )
    }

    suspend fun refreshWebsiteVisitorStats(): Result<WebsiteVisitorStats> {
        return try {
            requireSignedIn()
            val statsSnap = runCatching {
                visitorStatsDoc.get(Source.SERVER).await()
            }.getOrNull()
            val visitDocs = runCatching {
                pageVisitsCol
                    .limit(2000)
                    .get(Source.SERVER)
                    .await()
                    .documents
            }.getOrDefault(emptyList())
            val misroutedAlerts = runCatching {
                db.collection("loginAlerts")
                    .limit(300)
                    .get(Source.SERVER)
                    .await()
                    .documents
                    .filter { isPublicVisitFallbackDoc(it) }
            }.getOrDefault(emptyList())
            val misroutedAttempts = runCatching {
                db.collection("adminLoginAttempts")
                    .limit(300)
                    .get(Source.SERVER)
                    .await()
                    .documents
                    .filter { isPublicVisitFallbackDoc(it) }
            }.getOrDefault(emptyList())
            Result.success(computeVisitorStats(statsSnap, visitDocs + misroutedAlerts + misroutedAttempts))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "siteStats/visitors")
            Result.failure(friendlyException(e))
        }
    }

    private fun isPublicVisitFallbackDoc(doc: DocumentSnapshot): Boolean {
        val data = doc.data ?: return false
        val sourcePage = (data["sourcePage"] as? String)
            ?: (data["page"] as? String)
            ?: (data["path"] as? String)
            ?: (data["url"] as? String)
            ?: ""
        val platform = (data["platform"] as? String).orEmpty()
        val eventType = (data["attemptType"] as? String).orEmpty()
        val status = (data["status"] as? String).orEmpty()
        val reason = (data["reason"] as? String).orEmpty()
        return com.example.data.model.LoginSecurityAlert.isPublicWebsiteVisitEvent(
            sourcePage = sourcePage,
            platform = platform,
            eventType = eventType,
            status = status,
            reason = reason
        )
    }

    private fun extractVisitTimestampMillis(doc: DocumentSnapshot): Long {
        val data = doc.data ?: return 0L
        val fromTs = listOf("createdAt", "timestamp", "visitedAt", "updatedAt")
            .firstNotNullOfOrNull { field ->
                runCatching {
                    doc.getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
                }.getOrNull()
            }
        if (fromTs != null && fromTs > 0L) return fromTs
        val fromMillis = (data["timestampMillis"] as? Number)?.toLong()
            ?: (data["createdAtMillis"] as? Number)?.toLong()
            ?: (data["visitedAtMillis"] as? Number)?.toLong()
        if (fromMillis != null && fromMillis > 0L) return fromMillis
        val dateKey = (data["dateKey"] as? String).orEmpty().trim()
        if (dateKey.length == 10) {
            runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey)?.time
            }.getOrNull()?.let { if (it > 0L) return it }
        }
        return 0L
    }

    private fun computeVisitorStats(
        statsSnap: DocumentSnapshot?,
        visitDocs: List<DocumentSnapshot>
    ): WebsiteVisitorStats {
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

        var visitsTodayFromLog = 0L
        var visits7dFromLog = 0L
        var latestVisitMillis = 0L
        val uniqueIds = HashSet<String>()
        val seenDocKeys = HashSet<String>()
        val parsedRecords = ArrayList<PageVisitRecord>(visitDocs.size)

        visitDocs.forEach { doc ->
            val data = doc.data ?: return@forEach
            val dedupKey = "${doc.reference.path}"
            if (!seenDocKeys.add(dedupKey)) return@forEach
            val createdMillis = extractVisitTimestampMillis(doc)
            if (createdMillis > latestVisitMillis) {
                latestVisitMillis = createdMillis
            }
            val docDateKey = (data["dateKey"] as? String).orEmpty().trim()
            if (docDateKey == todayKey || (createdMillis >= startOfTodayMillis && createdMillis > 0L)) {
                visitsTodayFromLog += 1L
            }
            if (createdMillis >= sevenDaysAgoMillis && createdMillis > 0L) {
                visits7dFromLog += 1L
            }
            val visitorId = (data["visitorId"] as? String).orEmpty().trim()
                .ifBlank { (data["deviceId"] as? String).orEmpty().trim() }
                .ifBlank { (data["sessionId"] as? String).orEmpty().trim() }
            if (visitorId.isNotEmpty()) {
                uniqueIds.add(visitorId)
            } else {
                uniqueIds.add(doc.id)
            }
            val rawPage = (data["page"] as? String).orEmpty().trim()
                .ifBlank { (data["path"] as? String).orEmpty().trim() }
                .ifBlank { (data["sourcePage"] as? String).orEmpty().trim() }
                .ifBlank { (data["url"] as? String).orEmpty().trim() }
                .ifBlank { "/" }
            val normalizedPage = com.example.data.model.LoginSecurityAlert.normalizePagePath(rawPage).ifBlank { "/" }
            parsedRecords.add(
                PageVisitRecord(
                    id = (data["id"] as? String).orEmpty().ifBlank { doc.id },
                    page = normalizedPage,
                    visitorId = visitorId,
                    dateKey = docDateKey,
                    createdAtMillis = createdMillis
                )
            )
        }

        parsedRecords.sortByDescending { it.createdAtMillis }

        val statsData = statsSnap?.takeIf { it.exists() }?.data
        val counterTotal = (
            (statsData?.get("totalVisits") as? Number)
                ?: (statsData?.get("total") as? Number)
                ?: (statsData?.get("count") as? Number)
            )?.toLong()?.coerceAtLeast(0L) ?: 0L
        val counterUnique = (
            (statsData?.get("uniqueVisitors") as? Number)
                ?: (statsData?.get("unique") as? Number)
            )?.toLong()?.coerceAtLeast(0L) ?: 0L
        val counterTodayKey = (statsData?.get("todayKey") as? String).orEmpty().trim()
        val counterToday = if (counterTodayKey.isEmpty() || counterTodayKey == todayKey) {
            (
                (statsData?.get("todayVisits") as? Number)
                    ?: (statsData?.get("today") as? Number)
                )?.toLong()?.coerceAtLeast(0L) ?: 0L
        } else {
            0L
        }
        val counterUpdatedMillis = statsSnap?.takeIf { it.exists() }
            ?.let { snap ->
                runCatching {
                    snap.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
                        ?: snap.getTimestamp("lastVisitAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
                }.getOrNull()
            } ?: 0L

        val totalVisits = maxOf(counterTotal, parsedRecords.size.toLong())
        val todayVisits = maxOf(counterToday, visitsTodayFromLog).coerceAtMost(totalVisits)
        val last7Days = maxOf(visits7dFromLog, todayVisits).coerceAtMost(totalVisits)
        val uniqueVisitors = maxOf(counterUnique, uniqueIds.size.toLong()).coerceAtMost(totalVisits)
        val lastVisitAt = maxOf(latestVisitMillis, counterUpdatedMillis)

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

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private fun friendlyException(e: Exception): Exception {
        val firestoreEx = unwrapFirestoreException(e)
        return if (firestoreEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            IllegalStateException(
                "Admin access required: sign in with alifcarimports@gmail.com or add your email in Admin Access."
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
}

private fun DocumentSnapshot.toShowroomConfigSafe(): ShowroomConfig {
    val root = data ?: return ShowroomConfig()
    val bMap = root["branding"] as? Map<*, *>
    val hMap = root["hero"] as? Map<*, *>
    val tMap = root["tiktok"] as? Map<*, *>
    val cMap = root["contact"] as? Map<*, *>

    val defaults = ShowroomConfig()
    val branding = if (bMap != null) {
        BrandingConfig(
            companyName = (bMap["companyName"] as? String).orEmpty().ifBlank { defaults.branding.companyName },
            logoUrl = listOf("logoUrl", "logoURL", "logo_url", "logo", "imageUrl", "image")
                .firstNotNullOfOrNull { extractPortalImageUrl(bMap[it]) }
                .orEmpty(),
            tagline = (bMap["tagline"] as? String).orEmpty()
        )
    } else defaults.branding

    val hero = if (hMap != null) {
        HeroConfig(
            kicker = (hMap["kicker"] as? String).orEmpty(),
            title = (hMap["title"] as? String).orEmpty().ifBlank { defaults.hero.title },
            description = (hMap["description"] as? String).orEmpty(),
            imageUrl = listOf("imageUrl", "imageURL", "image_url", "image", "bannerUrl", "backgroundImage", "coverImage")
                .firstNotNullOfOrNull { extractPortalImageUrl(hMap[it]) }
                .orEmpty(),
            ctaLabel = (hMap["ctaLabel"] as? String).orEmpty().ifBlank { defaults.hero.ctaLabel }
        )
    } else defaults.hero

    val tiktok = if (tMap != null) {
        TikTokSectionConfig(
            title = (tMap["title"] as? String).orEmpty().ifBlank { defaults.tiktok.title },
            description = (tMap["description"] as? String).orEmpty(),
            profileUrl = (tMap["profileUrl"] as? String).orEmpty(),
            handle = (tMap["handle"] as? String).orEmpty()
        )
    } else defaults.tiktok

    val contact = if (cMap != null) {
        fun firstString(vararg keys: String): String =
            keys.firstNotNullOfOrNull { (cMap[it] as? String)?.trim()?.takeIf(String::isNotEmpty) }.orEmpty()

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
    } else defaults.contact

    val updatedAt = runCatching {
        getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.getOrNull()

    return ShowroomConfig(
        branding = branding,
        hero = hero,
        tiktok = tiktok,
        contact = contact,
        updatedAt = updatedAt
    )
}

private fun DocumentSnapshot.toTikTokPostSafe(): TikTokPost? {
    val root = data ?: return null
    val docId = (root["id"] as? String)?.takeIf { it.isNotBlank() } ?: id
    val sortOrder = when (val raw = root["sortOrder"]) {
        is Number -> raw.toLong()
        is String -> raw.trim().toLongOrNull() ?: 0L
        else -> 0L
    }
    val updatedAt = runCatching {
        getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.getOrNull()

    val resolvedCover = listOf(
        "coverImageUrl", "coverImage", "coverUrl", "cover",
        "thumbnailUrl", "thumbnail", "thumbUrl", "imageUrl", "image", "photoUrl", "photo"
    ).firstNotNullOfOrNull { extractPortalImageUrl(root[it]) }.orEmpty()

    return TikTokPost(
        id = docId,
        title = (root["title"] as? String).orEmpty(),
        caption = (root["caption"] as? String).orEmpty(),
        tiktokUrl = (root["tiktokUrl"] as? String).orEmpty(),
        coverImageUrl = resolvedCover,
        sortOrder = sortOrder,
        updatedAt = updatedAt
    )
}

private fun extractPortalImageUrl(raw: Any?): String? = when (raw) {
    is String -> raw.trim().takeIf { it.isNotEmpty() }
    is Map<*, *> -> listOf("url", "imageUrl", "downloadURL", "downloadUrl", "src", "secure_url", "path")
        .firstNotNullOfOrNull { (raw[it] as? String)?.trim()?.takeIf(String::isNotEmpty) }
    is List<*> -> raw.firstNotNullOfOrNull { extractPortalImageUrl(it) }
    else -> null
}

private fun DocumentSnapshot.toInquirySafe(): CustomerInquiry? {
    val root = data ?: return null
    val docId = (root["id"] as? String)?.takeIf { it.isNotBlank() } ?: id
    val createdAt = runCatching {
        getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.getOrNull()

    return CustomerInquiry(
        id = docId,
        name = (root["name"] as? String).orEmpty(),
        phone = (root["phone"] as? String).orEmpty(),
        email = (root["email"] as? String).orEmpty(),
        vehicleTitle = (root["vehicleTitle"] as? String).orEmpty(),
        message = (root["message"] as? String).orEmpty(),
        createdAt = createdAt
    )
}
