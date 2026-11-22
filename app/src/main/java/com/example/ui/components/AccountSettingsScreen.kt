package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActiveDeviceSession
import com.example.data.model.AuthenticatorStatus
import com.example.data.model.PendingAuthenticatorSetup
import com.example.data.model.SecurityLockHelper
import com.example.ui.theme.EmeraldAvailable
import com.example.ui.theme.EmeraldContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    sessions: List<ActiveDeviceSession>,
    loginAtMillis: Long,
    isMutatingPassword: Boolean,
    snackbarMessage: String?,
    onConsumeSnackbar: () -> Unit,
    onDeactivateDevice: (sessionId: String, totpCode: String) -> Result<Unit>,
    onChangePassword: (oldPassword: String, newPassword: String, confirmPassword: String, totpCode: String, onComplete: (Result<String>) -> Unit) -> Unit,
    onOpenContactSocialLocation: () -> Unit,
    onOpenAdminAccess: () -> Unit,
    onSignOutClick: () -> Unit,
    onBack: () -> Unit,
    securitySyncError: String? = null,
    isRefreshingSessions: Boolean = false,
    onRefreshSessions: () -> Unit = {},
    isSigningOutAll: Boolean = false,
    onSignOutAllDevicesClick: (totpCode: String, onResult: (Result<Int>) -> Unit) -> Unit = { _, _ -> },
    authenticatorStatus: AuthenticatorStatus = AuthenticatorStatus(),
    isMutatingAuthenticator: Boolean = false,
    onCreatePendingAuthenticatorSetup: () -> Result<PendingAuthenticatorSetup> = {
        Result.failure(IllegalStateException("Authenticator setup unavailable"))
    },
    onEnableAuthenticator: (secretBase32: String, code: String, onComplete: (Result<String>) -> Unit) -> Unit = { _, _, _ -> },
    onDisableAuthenticator: (code: String, onComplete: (Result<String>) -> Unit) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeSnackbar()
    }

    LaunchedEffect(Unit) {
        onRefreshSessions()
    }

    val websiteSessionsCount = remember(sessions) { sessions.count { it.isWebsiteSession } }
    val mobileSessionsCount = remember(sessions) { sessions.count { !it.isWebsiteSession } }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(loginAtMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    val isUnlocked = SecurityLockHelper.isUnlockedAfter24Hours(loginAtMillis, nowMillis)
    val remainingCountdown = SecurityLockHelper.formatUnlockCountdown(
        SecurityLockHelper.remainingLockMillis(loginAtMillis, nowMillis)
    )

    var selectedSessionDetail by remember { mutableStateOf<ActiveDeviceSession?>(null) }
    var removeDeviceTotpInput by remember { mutableStateOf("") }
    var removeDeviceDialogError by remember { mutableStateOf<String?>(null) }
    var deviceActionFeedback by remember { mutableStateOf<String?>(null) }
    var showSignOutAllConfirmDialog by remember { mutableStateOf(false) }
    var signOutAllTotpInput by remember { mutableStateOf("") }
    var signOutAllDialogError by remember { mutableStateOf<String?>(null) }

    var showChangePasswordForm by remember { mutableStateOf(false) }
    var oldPasswordInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var passwordTotpInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

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
                        modifier = Modifier.testTag("close_settings_screen_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Text(
                        text = "Settings",
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
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            ) {
                item(key = "settings_unlock_banner") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            color = if (isUnlocked) EmeraldContainer else MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(
                                1.dp,
                                if (isUnlocked) EmeraldAvailable.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isUnlocked) Icons.Default.CheckCircle else Icons.Default.LockClock,
                                    contentDescription = null,
                                    tint = if (isUnlocked) EmeraldAvailable else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isUnlocked) {
                                            "24-Hour Security Actions Unlocked"
                                        } else {
                                            "Access will unlock after 24 hours"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isUnlocked) EmeraldAvailable else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = if (isUnlocked) {
                                            "You can now manage active sessions or change your password."
                                        } else {
                                            "Removing other active devices and changing password unlock 24h after sign-in ($remainingCountdown)."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ACTIVE DEVICES (${sessions.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = onRefreshSessions,
                                enabled = !isRefreshingSessions,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("refresh_active_devices_button")
                            ) {
                                if (isRefreshingSessions) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh active devices",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SessionChannelChip(
                                icon = Icons.Default.PhoneAndroid,
                                label = "Mobile App: $mobileSessionsCount",
                                highlighted = mobileSessionsCount > 0,
                                modifier = Modifier.weight(1f)
                            )
                            SessionChannelChip(
                                icon = Icons.Default.Language,
                                label = "Website: $websiteSessionsCount",
                                highlighted = websiteSessionsCount > 0,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        securitySyncError?.let { syncErr ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = syncErr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                )
                            }
                        }

                        deviceActionFeedback?.let { feedback ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = feedback,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                )
                            }
                        }
                    }
                }

                if (sessions.isEmpty()) {
                    item(key = "empty_sessions") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = "No active admin sessions recorded yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }
                    }
                } else {
                    items(items = sessions, key = { "session_${it.id}" }) { session ->
                        val isCurrent = session.isCurrentDevice
                        val isWebsite = session.isWebsiteSession
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = when {
                                    isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                    isWebsite -> EmeraldAvailable.copy(alpha = 0.65f)
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    deviceActionFeedback = null
                                    selectedSessionDetail = session
                                }
                                .testTag("active_device_card_${session.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = if (isCurrent) 9.dp else 10.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isWebsite) {
                                        Icons.Default.Language
                                    } else {
                                        Icons.Default.PhoneAndroid
                                    },
                                    contentDescription = if (isWebsite) "Website session" else "Mobile app session",
                                    tint = when {
                                        isCurrent -> MaterialTheme.colorScheme.primary
                                        isWebsite -> EmeraldAvailable
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = session.deviceName,
                                            style = if (isCurrent) {
                                                MaterialTheme.typography.bodySmall
                                            } else {
                                                MaterialTheme.typography.titleSmall
                                            },
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        when {
                                            isCurrent -> {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = CircleShape
                                                ) {
                                                    Text(
                                                        text = "Current Device",
                                                        fontSize = 9.sp,
                                                        lineHeight = 11.sp,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            isWebsite -> {
                                                Surface(
                                                    color = EmeraldContainer,
                                                    shape = CircleShape
                                                ) {
                                                    Text(
                                                        text = "Website",
                                                        fontSize = 9.sp,
                                                        lineHeight = 11.sp,
                                                        color = EmeraldAvailable,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            else -> {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = CircleShape
                                                ) {
                                                    Text(
                                                        text = "Mobile App",
                                                        fontSize = 9.sp,
                                                        lineHeight = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = session.email.ifBlank { "Admin email unavailable" },
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = session.platform,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "•",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = session.location,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "•",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatEpochMillis(session.loginAtMillis),
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "View device details",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (websiteSessionsCount == 0) {
                        item(key = "website_sessions_status_note") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "No website admin sessions connected",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Anyone signed in on the website admin page appears here automatically with their email once the website logs sessions to Firestore (adminSessions).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "active_devices_sign_out_all") {
                    Button(
                        onClick = { showSignOutAllConfirmDialog = true },
                        enabled = !isSigningOutAll,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("active_devices_sign_out_all_button")
                    ) {
                        if (isSigningOutAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onError,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Signing Out All Devices…", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out All Devices (App & Website)", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item(key = "security_and_management_footer") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "SECURITY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )

                        AuthenticatorSecurityCard(
                            status = authenticatorStatus,
                            isMutating = isMutatingAuthenticator,
                            onCreatePendingSetup = onCreatePendingAuthenticatorSetup,
                            onEnableAuthenticator = onEnableAuthenticator,
                            onDisableAuthenticator = onDisableAuthenticator,
                            onShowQuickFeedback = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showChangePasswordForm = !showChangePasswordForm }
                                .testTag("toggle_change_password_section")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Change Password (App & Website)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isUnlocked) {
                                                "One shared admin password for both Mobile App and Website Admin page. Changing it signs out other devices."
                                            } else {
                                                "Access will unlock after 24 hours ($remainingCountdown)"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null
                                    )
                                }

                                AnimatedVisibility(
                                    visible = showChangePasswordForm,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = oldPasswordInput,
                                            onValueChange = { oldPasswordInput = it },
                                            label = { Text("Previous Password") },
                                            singleLine = true,
                                            visualTransformation = if (passwordVisible) {
                                                VisualTransformation.None
                                            } else {
                                                PasswordVisualTransformation()
                                            },
                                            trailingIcon = {
                                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                    Icon(
                                                        imageVector = if (passwordVisible) {
                                                            Icons.Default.VisibilityOff
                                                        } else {
                                                            Icons.Default.Visibility
                                                        },
                                                        contentDescription = "Toggle password visibility"
                                                    )
                                                }
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("old_password_input")
                                        )
                                        OutlinedTextField(
                                            value = newPasswordInput,
                                            onValueChange = { newPasswordInput = it },
                                            label = { Text("New Password") },
                                            singleLine = true,
                                            visualTransformation = if (passwordVisible) {
                                                VisualTransformation.None
                                            } else {
                                                PasswordVisualTransformation()
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("new_password_input")
                                        )
                                        OutlinedTextField(
                                            value = confirmPasswordInput,
                                            onValueChange = { confirmPasswordInput = it },
                                            label = { Text("Confirm New Password") },
                                            singleLine = true,
                                            isError = confirmPasswordInput.isNotEmpty() && confirmPasswordInput != newPasswordInput,
                                            visualTransformation = if (passwordVisible) {
                                                VisualTransformation.None
                                            } else {
                                                PasswordVisualTransformation()
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("confirm_password_input")
                                        )

                                        if (authenticatorStatus.enabled) {
                                            TotpCodeInputField(
                                                value = passwordTotpInput,
                                                onValueChange = { passwordTotpInput = it },
                                                label = "Google Authenticator 6-Digit Code",
                                                enabled = !isMutatingPassword,
                                                supportingText = "Required because Google Authenticator (2FA) is enabled",
                                                testTag = "change_password_totp_input"
                                            )
                                        }

                                        passwordMessage?.let { (isOk, msg) ->
                                            Surface(
                                                color = if (isOk) EmeraldContainer else MaterialTheme.colorScheme.errorContainer,
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text(
                                                    text = msg,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isOk) EmeraldAvailable else MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(10.dp)
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                passwordMessage = null
                                                onChangePassword(
                                                    oldPasswordInput,
                                                    newPasswordInput,
                                                    confirmPasswordInput,
                                                    passwordTotpInput
                                                ) { result ->
                                                    result.onSuccess { okMsg ->
                                                        passwordMessage = true to okMsg
                                                        oldPasswordInput = ""
                                                        newPasswordInput = ""
                                                        confirmPasswordInput = ""
                                                        passwordTotpInput = ""
                                                    }.onFailure { err ->
                                                        passwordMessage = false to (err.localizedMessage ?: "Failed to change password")
                                                    }
                                                }
                                            },
                                            enabled = !isMutatingPassword,
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("submit_change_password_button")
                                        ) {
                                            if (isMutatingPassword) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Updating…")
                                            } else {
                                                Text("Update App & Website Password", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = "PORTAL MANAGEMENT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )

                        SettingsNavigationRow(
                            icon = Icons.Default.AdminPanelSettings,
                            title = "Admin Access",
                            subtitle = "Manage authorized admin emails for website & app",
                            onClick = onOpenAdminAccess,
                            testTag = "settings_admin_access_row"
                        )

                        SettingsNavigationRow(
                            icon = Icons.Default.Contacts,
                            title = "Contact, Social Media & Location",
                            subtitle = "Edit social media links, contact details, location & working days",
                            onClick = onOpenContactSocialLocation,
                            testTag = "settings_contact_social_location_row"
                        )

                        Button(
                            onClick = { showSignOutAllConfirmDialog = true },
                            enabled = !isSigningOutAll,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("settings_sign_out_all_devices_button")
                        ) {
                            if (isSigningOutAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onError,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Signing Out All Devices…", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign Out All Devices (App & Website)", fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onSignOutClick,
                            enabled = !isSigningOutAll,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("settings_sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out (This Phone)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showSignOutAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSigningOutAll) {
                    showSignOutAllConfirmDialog = false
                    signOutAllTotpInput = ""
                    signOutAllDialogError = null
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Sign Out All Devices?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This immediately revokes all active Mobile App and Website sessions and enforces a server-side cutoff that blocks even older, un-updated app versions and browser tabs until they sign in again fresh.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (authenticatorStatus.enabled) {
                        TotpCodeInputField(
                            value = signOutAllTotpInput,
                            onValueChange = {
                                signOutAllTotpInput = it
                                signOutAllDialogError = null
                            },
                            label = "Google Authenticator 6-Digit Code",
                            enabled = !isSigningOutAll,
                            isError = signOutAllDialogError != null,
                            testTag = "sign_out_all_totp_input"
                        )
                    }
                    signOutAllDialogError?.let { errMsg ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = errMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        signOutAllDialogError = null
                        onSignOutAllDevicesClick(signOutAllTotpInput) { result ->
                            result.onSuccess {
                                showSignOutAllConfirmDialog = false
                                signOutAllTotpInput = ""
                                signOutAllDialogError = null
                            }.onFailure { err ->
                                signOutAllDialogError = err.localizedMessage
                                    ?: "Valid 6-digit Google Authenticator code is required."
                            }
                        }
                    },
                    enabled = !isSigningOutAll,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_sign_out_all_devices_button")
                ) {
                    Text(
                        text = if (isSigningOutAll) "Verifying…" else "Sign Out All",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSignOutAllConfirmDialog = false
                        signOutAllTotpInput = ""
                        signOutAllDialogError = null
                    },
                    enabled = !isSigningOutAll
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedSessionDetail?.let { session ->
        AlertDialog(
            onDismissRequest = {
                selectedSessionDetail = null
                removeDeviceTotpInput = ""
                removeDeviceDialogError = null
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = if (session.isCurrentDevice) {
                        "Current Device (This Phone)"
                    } else {
                        session.deviceName
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsDetailRow(
                        icon = Icons.Default.Person,
                        text = "Signed in as: ${session.email.ifBlank { "Admin email unavailable" }}"
                    )
                    SettingsDetailRow(
                        icon = if (session.isWebsiteSession) Icons.Default.Language else Icons.Default.PhoneAndroid,
                        text = "Platform: ${session.platform}"
                    )
                    SettingsDetailRow(Icons.Default.Devices, "Device: ${session.deviceName}")
                    SettingsDetailRow(Icons.Default.LocationOn, "Location: ${session.location}")
                    SettingsDetailRow(Icons.Default.Schedule, "Logged in: ${formatEpochMillis(session.loginAtMillis)}")
                    if (!session.isCurrentDevice && !isUnlocked) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "Access will unlock after 24 hours ($remainingCountdown).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else if (!session.isCurrentDevice && authenticatorStatus.enabled) {
                        TotpCodeInputField(
                            value = removeDeviceTotpInput,
                            onValueChange = {
                                removeDeviceTotpInput = it
                                removeDeviceDialogError = null
                            },
                            label = "Google Authenticator 6-Digit Code",
                            isError = removeDeviceDialogError != null,
                            supportingText = "Paste 6-digit code from Google Authenticator to log out this device",
                            testTag = "remove_device_totp_input"
                        )
                    } else if (session.isCurrentDevice) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "Protected so you don't accidentally log out your own active phone while managing other sessions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    removeDeviceDialogError?.let { errMsg ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = errMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!session.isCurrentDevice) {
                    Button(
                        onClick = {
                            removeDeviceDialogError = null
                            val result = onDeactivateDevice(session.id, removeDeviceTotpInput)
                            result.onSuccess {
                                selectedSessionDetail = null
                                removeDeviceTotpInput = ""
                                removeDeviceDialogError = null
                            }.onFailure { err ->
                                val msg = err.localizedMessage
                                    ?: "Access will unlock after 24 hours."
                                if (authenticatorStatus.enabled) {
                                    removeDeviceDialogError = msg
                                } else {
                                    selectedSessionDetail = null
                                    deviceActionFeedback = msg
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.testTag("confirm_deactivate_device_button")
                    ) {
                        Text("Log Out Device")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedSessionDetail = null
                        removeDeviceTotpInput = ""
                        removeDeviceDialogError = null
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun SessionChannelChip(
    icon: ImageVector,
    label: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsDetailRow(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
