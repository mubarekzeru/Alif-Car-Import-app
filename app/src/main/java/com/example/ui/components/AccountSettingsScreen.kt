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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
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
import com.example.data.model.SecurityLockHelper
import com.example.ui.theme.EmeraldAvailable
import com.example.ui.theme.EmeraldContainer
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    sessions: List<ActiveDeviceSession>,
    loginAtMillis: Long,
    isMutatingPassword: Boolean,
    snackbarMessage: String?,
    onConsumeSnackbar: () -> Unit,
    onDeactivateDevice: (String) -> Result<Unit>,
    onChangePassword: (oldPassword: String, newPassword: String, confirmPassword: String, onComplete: (Result<String>) -> Unit) -> Unit,
    onOpenContactSocialLocation: () -> Unit,
    onOpenAdminAccess: () -> Unit,
    onSignOutClick: () -> Unit,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeSnackbar()
    }

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
    var deviceActionFeedback by remember { mutableStateOf<String?>(null) }

    var showChangePasswordForm by remember { mutableStateOf(false) }
    var oldPasswordInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
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

                        Text(
                            text = "ACTIVE DEVICES (${sessions.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )

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
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.outline
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
                                        vertical = if (isCurrent) 8.dp else 10.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(if (isCurrent) 16.dp else 18.dp)
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
                                        if (isCurrent) {
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
                }

                item(key = "security_and_management_footer") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "SECURITY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
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
                                            text = "Change Password",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isUnlocked) {
                                                "Enter previous password and confirm your new password"
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
                                                    confirmPasswordInput
                                                ) { result ->
                                                    result.onSuccess { okMsg ->
                                                        passwordMessage = true to okMsg
                                                        oldPasswordInput = ""
                                                        newPasswordInput = ""
                                                        confirmPasswordInput = ""
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
                                                Text("Update Password", fontWeight = FontWeight.Bold)
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

                        OutlinedButton(
                            onClick = onSignOutClick,
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
                            Text("Sign Out", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    selectedSessionDetail?.let { session ->
        AlertDialog(
            onDismissRequest = { selectedSessionDetail = null },
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
                    SettingsDetailRow(Icons.Default.Devices, "Device: ${session.deviceName}")
                    SettingsDetailRow(Icons.Default.AdminPanelSettings, "Platform: ${session.platform}")
                    SettingsDetailRow(Icons.Default.LocationOn, "Location: ${session.location}")
                    SettingsDetailRow(Icons.Default.Schedule, "Logged in: ${formatEpochMillis(session.loginAtMillis)}")
                    if (session.email.isNotBlank()) {
                        Text(
                            text = "Account: ${session.email}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                }
            },
            confirmButton = {
                if (!session.isCurrentDevice) {
                    Button(
                        onClick = {
                            val result = onDeactivateDevice(session.id)
                            selectedSessionDetail = null
                            result.onFailure { err ->
                                deviceActionFeedback = err.localizedMessage
                                    ?: "Access will unlock after 24 hours."
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
                TextButton(onClick = { selectedSessionDetail = null }) {
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
