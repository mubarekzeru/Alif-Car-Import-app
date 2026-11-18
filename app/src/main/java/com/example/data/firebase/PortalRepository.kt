package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AdminAccessConfig
import com.example.data.model.BrandingConfig
import com.example.data.model.ContactConfig
import com.example.data.model.CustomerInquiry
import com.example.data.model.HeroConfig
import com.example.data.model.ShowroomConfig
import com.example.data.model.TikTokPost
import com.example.data.model.TikTokPostDraft
import com.example.data.model.TikTokSectionConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
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
            logoUrl = (bMap["logoUrl"] as? String).orEmpty(),
            tagline = (bMap["tagline"] as? String).orEmpty()
        )
    } else defaults.branding

    val hero = if (hMap != null) {
        HeroConfig(
            kicker = (hMap["kicker"] as? String).orEmpty(),
            title = (hMap["title"] as? String).orEmpty().ifBlank { defaults.hero.title },
            description = (hMap["description"] as? String).orEmpty(),
            imageUrl = (hMap["imageUrl"] as? String).orEmpty(),
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
        ContactConfig(
            addressLine = (cMap["addressLine"] as? String).orEmpty(),
            cityCountry = (cMap["cityCountry"] as? String).orEmpty(),
            hours = (cMap["hours"] as? String).orEmpty(),
            phone = (cMap["phone"] as? String).orEmpty(),
            whatsapp = (cMap["whatsapp"] as? String).orEmpty(),
            telegram = (cMap["telegram"] as? String).orEmpty(),
            instagram = (cMap["instagram"] as? String).orEmpty(),
            facebook = (cMap["facebook"] as? String).orEmpty(),
            x = (cMap["x"] as? String).orEmpty(),
            email = (cMap["email"] as? String).orEmpty(),
            mapsUrl = (cMap["mapsUrl"] as? String).orEmpty(),
            landmarkNote = (cMap["landmarkNote"] as? String).orEmpty()
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

    return TikTokPost(
        id = docId,
        title = (root["title"] as? String).orEmpty(),
        caption = (root["caption"] as? String).orEmpty(),
        tiktokUrl = (root["tiktokUrl"] as? String).orEmpty(),
        coverImageUrl = (root["coverImageUrl"] as? String).orEmpty(),
        sortOrder = sortOrder,
        updatedAt = updatedAt
    )
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
