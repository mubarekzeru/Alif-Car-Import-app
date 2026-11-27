package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.firebase.AdminAccessState
import com.example.data.firebase.InquiriesState
import com.example.data.model.AdminAccessConfig
import com.example.data.model.LoginAlertDecision
import com.example.data.model.LoginSecurityAlert
import com.example.ui.notifications.SecurityNotificationHelper
import com.example.ui.theme.AmberReserved
import com.example.ui.theme.AmberReservedContainer
import com.example.ui.theme.EmeraldAvailable
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

enum class NotificationCenterTab(val label: String) {
    LOGIN_ALERTS("Login Alerts"),
    INQUIRIES("Customer Inquiries")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    activeTab: NotificationCenterTab,
    alerts: List<LoginSecurityAlert>,
    inquiriesState: InquiriesState,
    adminAccessState: AdminAccessState = AdminAccessState.Loading,
    isMutatingAdminAccess: Boolean = false,
    currentSessionId: String = "",
    currentSessionVerifiedWithAuthenticator: Boolean = false,
    preselectedGrantEmail: String? = null,
    onConsumePreselectedGrantEmail: () -> Unit = {},
    snackbarMessage: String?,
    onConsumeSnackbar: () -> Unit,
    onSelectTab: (NotificationCenterTab) -> Unit,
    onConfirmItWasMe: (String) -> Unit,
    onRejectNotMe: (String) -> Unit,
    onGrantAdminAccess: (String) -> Unit = {},
    onRevokeAdminAccess: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(SecurityNotificationHelper.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || SecurityNotificationHelper.hasNotificationPermission(context)
    }

    val authorizedEmails = remember(adminAccessState) {
        val list = (adminAccessState as? AdminAccessState.Success)?.config?.adminEmails.orEmpty()
        (list + AdminAccessConfig.BOOTSTRAP_OWNER_EMAIL)
            .map { it.trim().lowercase(Locale.US) }
            .toSet()
    }

    var grantDialogEmail by remember { mutableStateOf<String?>(null) }
    var revokeDialogEmail by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(preselectedGrantEmail) {
        val candidate = preselectedGrantEmail?.trim()
        if (!candidate.isNullOrBlank()) {
            if (AdminAccessConfig.isValidAdminEmail(candidate)) {
                grantDialogEmail = candidate
            }
            onConsumePreselectedGrantEmail()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeSnackbar()
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
                        modifier = Modifier.testTag("notifications_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to home"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Admin Login Alerts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Admin page login attempts only (not visitor pages)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AmberReservedContainer),
                        border = BorderStroke(1.dp, AmberReserved.copy(alpha = 0.45f)),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Turn on Notifications",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Allow notifications so this phone alerts you immediately when someone tries to sign into the Admin Page.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("enable_notifications_button")
                                ) {
                                    Text("Allow Alerts", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        runCatching { context.startActivity(settingsIntent) }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("open_notification_settings_button")
                                ) {
                                    Text("App Settings")
                                }
                            }
                        }
                    }
                }

                LoginAlertsList(
                    alerts = alerts,
                    authorizedEmails = authorizedEmails,
                    isMutatingAdminAccess = isMutatingAdminAccess,
                    currentSessionId = currentSessionId,
                    currentSessionVerifiedWithAuthenticator = currentSessionVerifiedWithAuthenticator,
                    onConfirmItWasMe = onConfirmItWasMe,
                    onRejectNotMe = onRejectNotMe,
                    onRequestGrantAccess = { email -> grantDialogEmail = email },
                    onRequestRevokeAccess = { email -> revokeDialogEmail = email }
                )
            }
        }
    }

    val pendingGrant = grantDialogEmail
    if (pendingGrant != null) {
        val normalizedGrant = pendingGrant.trim().lowercase(Locale.US)
        val alreadyGranted = normalizedGrant in authorizedEmails
        val matchedAlert = alerts.firstOrNull {
            it.isUnauthorizedAttempt && it.email.trim().equals(normalizedGrant, ignoreCase = true)
        }

        AlertDialog(
            onDismissRequest = { if (!isMutatingAdminAccess) grantDialogEmail = null },
            icon = {
                Icon(
                    imageVector = if (alreadyGranted) Icons.Default.VerifiedUser else Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = if (alreadyGranted) EmeraldAvailable else MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = if (alreadyGranted) "Admin Access Already Granted" else "Grant Admin Access?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = normalizedGrant,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (matchedAlert != null) {
                                Text(
                                    text = "${matchedAlert.platform} • ${matchedAlert.deviceName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = matchedAlert.location,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = if (alreadyGranted) {
                            "This email is already in your Admin Access list and can sign in to the Mobile App and Website Admin Portal."
                        } else {
                            "Granting access will add this email to Admin Access so they can sign in and manage inventory, showroom details, and portal settings on both the Mobile App and Website Admin Page."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                if (alreadyGranted) {
                    Button(
                        onClick = { grantDialogEmail = null },
                        shape = CircleShape,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("grant_already_done_button")
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            onGrantAdminAccess(normalizedGrant)
                            grantDialogEmail = null
                        },
                        enabled = !isMutatingAdminAccess,
                        shape = CircleShape,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("confirm_grant_admin_access_button")
                    ) {
                        Text("Grant Access", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (!alreadyGranted) {
                    TextButton(
                        onClick = { grantDialogEmail = null },
                        enabled = !isMutatingAdminAccess,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("cancel_grant_admin_access_button")
                    ) {
                        Text("Keep Blocked")
                    }
                }
            }
        )
    }

    val pendingRevoke = revokeDialogEmail
    if (pendingRevoke != null) {
        val normalizedRevoke = pendingRevoke.trim().lowercase(Locale.US)
        AlertDialog(
            onDismissRequest = { if (!isMutatingAdminAccess) revokeDialogEmail = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.GppBad,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Revoke Admin Access?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Remove $normalizedRevoke from Admin Access? This account will immediately lose access to the Mobile App and Website Admin Page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRevokeAdminAccess(normalizedRevoke)
                        revokeDialogEmail = null
                    },
                    enabled = !isMutatingAdminAccess,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("confirm_revoke_admin_access_button")
                ) {
                    Text("Revoke Access", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { revokeDialogEmail = null },
                    enabled = !isMutatingAdminAccess,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("cancel_revoke_admin_access_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CustomerInquiriesTabContent(
    state: InquiriesState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Customer Inquiries",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (state is InquiriesState.Success && state.inquiries.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ) {
                        Text(
                            text = state.inquiries.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search customer, phone, or vehicle…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inquiries_search_input")
            )
        }

        when (state) {
            InquiriesState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 108.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            InquiriesState.OwnerOnly -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 108.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = "Owner Account Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Website security rules restrict listing customer inquiries to alifcarimports@gmail.com.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            is InquiriesState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .padding(bottom = 108.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is InquiriesState.Success -> {
                val query = searchQuery.trim().lowercase()
                val filtered = remember(state.inquiries, query) {
                    if (query.isEmpty()) {
                        state.inquiries
                    } else {
                        state.inquiries.filter {
                            it.name.lowercase().contains(query) ||
                                it.phone.lowercase().contains(query) ||
                                it.vehicleTitle.lowercase().contains(query) ||
                                it.message.lowercase().contains(query)
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 108.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = if (query.isEmpty()) "No customer inquiries yet" else "No matching inquiries",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Customer leads submitted from your website appear here live.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 108.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = filtered, key = { it.id }) { inquiry ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("inquiry_card_${inquiry.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = CircleShape,
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = inquiry.name.trim().take(1).uppercase().ifEmpty { "C" },
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = inquiry.name.ifBlank { "Customer" },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = inquiry.phone,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Text(
                                            text = formatTimestamp(inquiry.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (inquiry.vehicleTitle.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = CircleShape
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DirectionsCar,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = inquiry.vehicleTitle,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    if (inquiry.message.isNotBlank()) {
                                        Text(
                                            text = inquiry.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (inquiry.email.isNotBlank()) {
                                            Text(
                                                text = inquiry.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }

                                        if (inquiry.phone.isNotBlank()) {
                                            Button(
                                                onClick = {
                                                    val intent = Intent(
                                                        Intent.ACTION_DIAL,
                                                        Uri.parse("tel:${inquiry.phone.trim()}")
                                                    )
                                                    context.startActivity(intent)
                                                },
                                                shape = CircleShape,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ),
                                                modifier = Modifier.height(42.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Call,
                                                    contentDescription = "Call customer",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Call", fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginAlertsList(
    alerts: List<LoginSecurityAlert>,
    authorizedEmails: Set<String>,
    isMutatingAdminAccess: Boolean,
    currentSessionId: String,
    currentSessionVerifiedWithAuthenticator: Boolean,
    onConfirmItWasMe: (String) -> Unit,
    onRejectNotMe: (String) -> Unit,
    onRequestGrantAccess: (String) -> Unit,
    onRequestRevokeAccess: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (alerts.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        text = "No admin login alerts yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Alerts appear here only when someone signs in or attempts to log into the Admin Page. Website visitor traffic is counted silently in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            alerts.forEach { alert ->
                val normalizedEmail = alert.email.trim().lowercase(Locale.US)
                val isValidCandidateEmail = AdminAccessConfig.isValidAdminEmail(alert.email)
                val isAlreadyGranted = alert.isUnauthorizedAttempt &&
                    isValidCandidateEmail &&
                    normalizedEmail in authorizedEmails
                val isBootstrapOwner = normalizedEmail == AdminAccessConfig.BOOTSTRAP_OWNER_EMAIL
                val isVerifiedOwnerAuthLogin = alert.isOwnerAuthenticatorVerified || (
                    !alert.isUnauthorizedAttempt &&
                        isBootstrapOwner &&
                        currentSessionVerifiedWithAuthenticator &&
                        currentSessionId.isNotBlank() &&
                        alert.sessionId == currentSessionId
                    )

                val (badgeLabel, badgeBg, badgeFg) = when {
                    alert.isUnauthorizedAttempt && isAlreadyGranted ->
                        Triple("Admin Access Granted", EmeraldContainer, EmeraldAvailable)
                    alert.isUnauthorizedAttempt ->
                        Triple(
                            "Blocked Admin Attempt",
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.colorScheme.error
                        )
                    isVerifiedOwnerAuthLogin ->
                        Triple("Verified • Authenticator", EmeraldContainer, EmeraldAvailable)
                    else -> when (alert.decision) {
                        LoginAlertDecision.PENDING ->
                            Triple(alert.decision.label, AmberReservedContainer, AmberReserved)
                        LoginAlertDecision.CONFIRMED_ME ->
                            Triple(alert.decision.label, EmeraldContainer, EmeraldAvailable)
                        LoginAlertDecision.REJECTED_NOT_ME ->
                            Triple(
                                alert.decision.label,
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.error
                            )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        width = 1.dp,
                        color = when {
                            (alert.isUnauthorizedAttempt && isAlreadyGranted) || isVerifiedOwnerAuthLogin ->
                                EmeraldAvailable.copy(alpha = 0.45f)
                            alert.isUnauthorizedAttempt ->
                                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.outline
                        }
                    ),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_alert_card_${alert.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = badgeBg,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeFg,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = alert.platform,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (alert.email.isNotBlank()) {
                            Text(
                                text = alert.email,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = alert.deviceName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = alert.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = formatEpochMillis(alert.timestampMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (alert.sourcePage.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Page: ${alert.sourcePage}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (alert.isUnauthorizedAttempt && isValidCandidateEmail) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            if (isAlreadyGranted) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = EmeraldAvailable,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Access granted — this account can now sign in",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = EmeraldAvailable
                                        )
                                    }
                                    if (!isBootstrapOwner) {
                                        TextButton(
                                            onClick = { onRequestRevokeAccess(normalizedEmail) },
                                            enabled = !isMutatingAdminAccess,
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("revoke_admin_access_${alert.id}")
                                        ) {
                                            Text(
                                                text = "Revoke",
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { onRequestGrantAccess(normalizedEmail) },
                                        enabled = !isMutatingAdminAccess,
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("grant_admin_access_${alert.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AdminPanelSettings,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Grant Admin Access", fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "Blocked from Admin Page. Leave as-is to keep blocked, or tap above to allow this email as an admin.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (isVerifiedOwnerAuthLogin) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("alert_authenticator_verified_${alert.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = EmeraldAvailable,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Signed in with ${AdminAccessConfig.BOOTSTRAP_OWNER_EMAIL} & Google Authenticator",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = EmeraldAvailable
                                )
                            }
                        } else if (!alert.isUnauthorizedAttempt && alert.decision == LoginAlertDecision.PENDING) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onConfirmItWasMe(alert.id) },
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, EmeraldAvailable),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = EmeraldAvailable
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("alert_it_was_me_${alert.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("It was me", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { onRejectNotMe(alert.id) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("alert_not_me_${alert.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GppBad,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("It wasn't me", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
