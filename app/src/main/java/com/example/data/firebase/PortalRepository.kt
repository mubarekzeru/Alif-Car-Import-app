package com.example.data.firebase

import android.content.Context
import com.example.data.model.AdminAccessConfig
import com.example.data.model.CustomerInquiry
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import org.json.JSONArray
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
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    suspend fun refreshWebsiteVisitorStats(): Result<WebsiteVisitorStats> {
        if (auth.currentUser == null) {
            val empty = WebsiteVisitorStats(isLoaded = true)
            _visitorStats.value = empty
            return Result.success(empty)
        }
        return try {
            val stats = apiClient.fetchAnalytics()
            _visitorStats.value = stats
            // Keep inquiries fresh alongside analytics when refreshing Interactions
            runCatching { refreshInquiries() }
            Result.success(stats)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!_visitorStats.value.isLoaded) {
                _visitorStats.value = WebsiteVisitorStats(isLoaded = true)
            }
            Result.failure(friendlyException(e))
        }
    }

    fun observeAdminAccess(): Flow<AdminAccessState> = flow {
        if (auth.currentUser == null) {
            emit(AdminAccessState.PermissionDenied)
            return@flow
        }
        _adminAccessState.value = loadLocalAdminAccessState()
        emitAll(_adminAccessState)
    }

    suspend fun saveAdminEmails(emails: List<String>): Result<Unit> {
        return try {
            requireSignedIn()
            val normalized = AdminAccessConfig.normalizeEmails(emails)
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
            val existing = loadStoredAdminEmails()
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
            val existing = AdminAccessConfig.normalizeEmails(loadStoredAdminEmails())
            if (target !in existing) {
                return Result.success(false)
            }
            val updated = existing.filterNot { it == target }
            persistLocalAdminEmails(updated)
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendlyException(e))
        }
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

    private fun persistLocalAdminEmails(emails: List<String>) {
        val now = System.currentTimeMillis()
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
                "Admin access required: sign in with alifcarimports@gmail.com or an authorized admin account."
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
        const val PREFS_NAME = "alif_portal_prefs"
        const val KEY_ADMIN_EMAILS_JSON = "admin_emails_json"
        const val KEY_ADMIN_EMAILS_UPDATED_AT = "admin_emails_updated_at"
    }
}
