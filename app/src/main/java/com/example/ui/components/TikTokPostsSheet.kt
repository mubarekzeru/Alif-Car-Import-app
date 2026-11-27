package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.example.data.model.TikTokPost
import com.example.data.model.TikTokPostDraft
import com.example.data.network.TikTokThumbnailResolver
import com.example.ui.theme.WarmCoralTop
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTikTokScreen(
    editingPost: TikTokPost?,
    defaultSortOrder: Long,
    isSaving: Boolean,
    snackbarMessage: String?,
    onConsumeSnackbar: () -> Unit,
    onBack: () -> Unit,
    onSave: (editingPostId: String?, draft: TikTokPostDraft) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeSnackbar()
    }

    var titleInput by remember(editingPost?.id) { mutableStateOf(editingPost?.title ?: "") }
    var captionInput by remember(editingPost?.id) { mutableStateOf(editingPost?.caption ?: "") }
    var tiktokUrlInput by remember(editingPost?.id) { mutableStateOf(editingPost?.tiktokUrl ?: "") }
    var coverImageUrl by remember(editingPost?.id) { mutableStateOf(editingPost?.coverImageUrl ?: "") }
    var sortOrderInput by remember(editingPost?.id) {
        mutableStateOf((editingPost?.sortOrder ?: defaultSortOrder).toString())
    }
    var isFetchingCover by remember(editingPost?.id) { mutableStateOf(false) }
    var coverStatusText by remember(editingPost?.id) {
        mutableStateOf(
            if (!editingPost?.coverImageUrl.isNullOrBlank()) {
                "Cover preview loaded from TikTok"
            } else {
                "Paste a TikTok video link to load its cover automatically"
            }
        )
    }
    var coverRefreshNonce by remember(editingPost?.id) { mutableIntStateOf(0) }

    LaunchedEffect(tiktokUrlInput, coverRefreshNonce) {
        val normalized = TikTokThumbnailResolver.normalizeTikTokUrl(tiktokUrlInput)
        if (normalized == null) {
            isFetchingCover = false
            coverStatusText = if (tiktokUrlInput.isBlank()) {
                "Paste a TikTok video link to load its cover automatically"
            } else {
                "Enter a valid tiktok.com link to load the video cover"
            }
            return@LaunchedEffect
        }

        val forceRefresh = coverRefreshNonce > 0
        val cached = if (!forceRefresh) TikTokThumbnailResolver.getCachedThumbnail(normalized) else null
        if (cached != null) {
            coverImageUrl = cached
            isFetchingCover = false
            coverStatusText = "Cover preview loaded from TikTok"
            return@LaunchedEffect
        }

        if (!forceRefresh) {
            delay(420L)
        }
        isFetchingCover = true
        coverStatusText = "Fetching cover image from TikTok…"
        val metadata = TikTokThumbnailResolver.resolveMetadata(normalized, forceRefresh = forceRefresh)
        isFetchingCover = false
        if (metadata != null && metadata.thumbnailUrl.isNotBlank()) {
            coverImageUrl = metadata.thumbnailUrl
            coverStatusText = "Cover preview loaded from TikTok"
            if (titleInput.isBlank() && metadata.title.isNotBlank()) {
                titleInput = metadata.title.take(200)
            }
        } else {
            coverStatusText = "Couldn't preview cover yet — tap Refresh or we'll retry on save"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("tiktok_form_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Text(
                        text = if (editingPost == null) "Add TikTok Video Link" else "Edit TikTok Video",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live TikTok Cover Preview Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(92.dp)
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1A1B1E)),
                            contentAlignment = Alignment.Center
                        ) {
                            val previewCandidates = remember(coverImageUrl) {
                                resolveCandidateImageUrls(coverImageUrl)
                            }
                            ResilientAppImage(
                                candidateUrls = previewCandidates,
                                contentDescription = "TikTok video cover preview",
                                cacheKeyPrefix = "tiktok_form_preview",
                                width = 320,
                                height = 440,
                                crossfadeMillis = 160,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF1A1B1E),
                                                    Color(0xFF2D2523),
                                                    WarmCoralTop.copy(alpha = 0.72f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            if (isFetchingCover) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White,
                                        strokeWidth = 2.5.dp
                                    )
                                }
                            } else if (coverImageUrl.isNotBlank()) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.45f),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(3.dp)
                                            .size(14.dp)
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Video Cover Preview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = coverStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (TikTokThumbnailResolver.normalizeTikTokUrl(tiktokUrlInput) != null) {
                                TextButton(
                                    onClick = { coverRefreshNonce += 1 },
                                    enabled = !isFetchingCover && !isSaving,
                                    modifier = Modifier.testTag("tiktok_refresh_cover_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Refresh Cover", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = tiktokUrlInput,
                    onValueChange = { if (it.length <= 500) tiktokUrlInput = it },
                    label = { Text("TikTok Video Link") },
                    placeholder = { Text("https://www.tiktok.com/@…/video/…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tiktok_url_input")
                )
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { if (it.length <= 200) titleInput = it },
                    label = { Text("Video Title") },
                    placeholder = { Text("2024 Lexus RX 350 Walkaround") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tiktok_title_input")
                )
                OutlinedTextField(
                    value = captionInput,
                    onValueChange = { if (it.length <= 2000) captionInput = it },
                    label = { Text("Caption") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tiktok_caption_input")
                )
                OutlinedTextField(
                    value = sortOrderInput,
                    onValueChange = { next ->
                        val digits = next.filter(Char::isDigit)
                        if (digits.length <= 4) sortOrderInput = digits
                    },
                    label = { Text("Display Order (0–1000)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val order = (sortOrderInput.toLongOrNull() ?: 0L).coerceIn(0L, 1000L)
                            val cleanUrl = TikTokThumbnailResolver.normalizeTikTokUrl(tiktokUrlInput)
                                ?: tiktokUrlInput.trim()
                            onSave(
                                editingPost?.id,
                                TikTokPostDraft(
                                    title = titleInput,
                                    caption = captionInput,
                                    tiktokUrl = cleanUrl,
                                    coverImageUrl = coverImageUrl,
                                    sortOrder = order
                                )
                            )
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp)
                            .testTag("save_tiktok_post_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving…")
                        } else {
                            Text(
                                text = if (editingPost == null) "Publish Video Link" else "Save Changes",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
