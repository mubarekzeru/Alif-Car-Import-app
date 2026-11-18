package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AdminAccessState
import com.example.data.firebase.InquiriesState
import com.example.data.firebase.PortalRepository
import com.example.data.model.ShowroomConfig
import com.example.data.model.TikTokPost
import com.example.data.model.TikTokPostDraft
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ActivePortalSheet {
    NONE,
    SHOWROOM,
    TIKTOK_POSTS,
    INQUIRIES,
    ADMIN_ACCESS
}

class PortalViewModel(
    private val repository: PortalRepository
) : ViewModel() {

    private val _activeSheet = MutableStateFlow(ActivePortalSheet.NONE)
    val activeSheet: StateFlow<ActivePortalSheet> = _activeSheet.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val isBootstrapOwner: Boolean
        get() = repository.isBootstrapOwner()

    val showroomConfig: StateFlow<ShowroomConfig> = repository.observeShowroomConfig()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing showroom config", firestoreError ?: error)
            emit(ShowroomConfig())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ShowroomConfig()
        )

    val tikTokPosts: StateFlow<List<TikTokPost>> = repository.observeTikTokPosts()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing tiktok posts", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val inquiriesState: StateFlow<InquiriesState> = repository.observeInquiries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = InquiriesState.Loading
        )

    val adminAccessState: StateFlow<AdminAccessState> = repository.observeAdminAccess()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AdminAccessState.Loading
        )

    fun openSheet(sheet: ActivePortalSheet) {
        _activeSheet.value = sheet
    }

    fun dismissSheet() {
        _activeSheet.value = ActivePortalSheet.NONE
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun saveShowroomConfig(config: ShowroomConfig) {
        if (_isMutating.value) return
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.saveShowroomConfig(config)
            _isMutating.value = false
            result.onSuccess {
                _activeSheet.value = ActivePortalSheet.NONE
                _snackbarMessage.value = "Showroom settings synced to website"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to save showroom settings"
            }
        }
    }

    fun saveTikTokPost(editingPostId: String?, draft: TikTokPostDraft, onSuccess: () -> Unit = {}) {
        if (_isMutating.value) return
        viewModelScope.launch {
            _isMutating.value = true
            val result = if (editingPostId == null) {
                repository.addTikTokPost(draft).map { }
            } else {
                repository.updateTikTokPost(editingPostId, draft)
            }
            _isMutating.value = false
            result.onSuccess {
                onSuccess()
                _snackbarMessage.value = if (editingPostId == null) {
                    "Published TikTok post to website"
                } else {
                    "Updated TikTok post"
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to save TikTok post"
            }
        }
    }

    fun deleteTikTokPost(post: TikTokPost) {
        if (_isMutating.value) return
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.deleteTikTokPost(post.id)
            _isMutating.value = false
            result.onSuccess {
                _snackbarMessage.value = "Removed TikTok post"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to delete TikTok post"
            }
        }
    }

    fun saveAdminEmails(emails: List<String>) {
        if (_isMutating.value) return
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.saveAdminEmails(emails)
            _isMutating.value = false
            result.onSuccess {
                _snackbarMessage.value = "Updated allowed admin emails"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to update admin emails"
            }
        }
    }

    private companion object {
        const val TAG = "PortalVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
