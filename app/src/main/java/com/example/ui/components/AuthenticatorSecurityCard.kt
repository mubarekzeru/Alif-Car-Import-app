package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AuthenticatorStatus
import com.example.data.model.PendingAuthenticatorSetup
import com.example.data.security.TotpAuthenticator
import com.example.ui.theme.EmeraldAvailable
import com.example.ui.theme.EmeraldContainer

@Composable
fun AuthenticatorSecurityCard(
    status: AuthenticatorStatus,
    isMutating: Boolean,
    onCreatePendingSetup: () -> Result<PendingAuthenticatorSetup>,
    onEnableAuthenticator: (secretBase32: String, code: String, onComplete: (Result<String>) -> Unit) -> Unit,
    onDisableAuthenticator: (code: String, onComplete: (Result<String>) -> Unit) -> Unit,
    onSetRequireAuthenticatorOnLogin: (requireOnLogin: Boolean, code: String, onComplete: (Result<String>) -> Unit) -> Unit = { _, _, _ -> },
    onShowQuickFeedback: (String) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    val context = LocalContext.current
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    var pendingSetup by remember { mutableStateOf<PendingAuthenticatorSetup?>(null) }
    var enableCodeInput by remember { mutableStateOf("") }
    var showDisableForm by remember { mutableStateOf(false) }
    var disableCodeInput by remember { mutableStateOf("") }
    var showDisableLoginCodeForm by remember { mutableStateOf(false) }
    var disableLoginCodeInput by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (status.enabled) EmeraldAvailable.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("toggle_authenticator_section")
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
                    imageVector = if (status.enabled) Icons.Default.VerifiedUser else Icons.Default.Security,
                    contentDescription = null,
                    tint = if (status.enabled) EmeraldAvailable else MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Authenticator (2FA)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (status.enabled) {
                            if (status.requireOnLogin) {
                                "6-digit Authenticator code is required to sign in to the Admin Portal, remove active devices, change password, or disable 2FA"
                            } else {
                                "Active for removing devices, changing password, and disabling 2FA (login authentication code is currently disabled)"
                            }
                        } else {
                            "Connect Google Authenticator to require a 6-digit code when signing in to the Admin Portal and for sensitive security actions"
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
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    feedbackMessage?.let { (isOk, msg) ->
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

                    if (!status.enabled) {
                        val setup = pendingSetup
                        if (setup == null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = "Enable Google Authenticator so signing in to the Admin Portal, removing active devices, signing out all devices, and changing your admin password require your live 6-digit code.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "Authentication Code for Login",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Enable Google Authenticator below first to turn login authentication codes on or off.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = false,
                                        enabled = false,
                                        onCheckedChange = {},
                                        modifier = Modifier.testTag("toggle_login_authenticator_code_switch_disabled")
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    feedbackMessage = null
                                    onCreatePendingSetup()
                                        .onSuccess { created ->
                                            pendingSetup = created
                                            enableCodeInput = ""
                                        }
                                        .onFailure { err ->
                                            feedbackMessage = false to (err.localizedMessage ?: "Unable to start Authenticator setup.")
                                        }
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("start_enable_authenticator_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enable Google Authenticator", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Step 1: Connect Link / Setup Key to Google Authenticator
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Step 1: Add to Google Authenticator",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Tap Open Google Authenticator, or copy the link / setup key below and paste it inside Google Authenticator (+ → Enter a setup key).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // Authenticator Link box
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.small,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "AUTHENTICATOR LINK",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = setup.otpAuthUri,
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Setup Key box (for Google Authenticator "Enter a setup key")
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.small,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "SETUP KEY (FOR MANUAL PASTE)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = setup.formattedSecret,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Account: ${setup.accountEmail} • Type: Time based",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                copyTextToClipboard(context, "Authenticator Link", setup.otpAuthUri)
                                                onShowQuickFeedback("Authenticator link copied")
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("copy_authenticator_link_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Copy Link", maxLines = 1)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                copyTextToClipboard(context, "Authenticator Setup Key", setup.secretBase32)
                                                onShowQuickFeedback("Setup key copied — paste in Google Authenticator")
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("copy_authenticator_key_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Key,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Copy Key", maxLines = 1)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val opened = runCatching {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(setup.otpAuthUri)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            }.isSuccess
                                            if (!opened) {
                                                copyTextToClipboard(context, "Authenticator Setup Key", setup.secretBase32)
                                                onShowQuickFeedback("Google Authenticator app not found directly — Setup Key copied instead!")
                                            }
                                        },
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("open_google_authenticator_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open in Google Authenticator", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            // Step 2: Verify 6-digit code to activate
                            Text(
                                text = "Step 2: Paste 6-Digit Code to Enable",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            TotpCodeInputField(
                                value = enableCodeInput,
                                onValueChange = { enableCodeInput = it },
                                label = "Google Authenticator 6-Digit Code",
                                enabled = !isMutating,
                                testTag = "authenticator_enable_code_input"
                            )

                            Button(
                                onClick = {
                                    feedbackMessage = null
                                    onEnableAuthenticator(setup.secretBase32, enableCodeInput) { result ->
                                        result.onSuccess { okMsg ->
                                            feedbackMessage = true to okMsg
                                            pendingSetup = null
                                            enableCodeInput = ""
                                        }.onFailure { err ->
                                            feedbackMessage = false to (err.localizedMessage ?: "Invalid verification code")
                                        }
                                    }
                                },
                                enabled = !isMutating && TotpAuthenticator.sanitizeCodeInput(enableCodeInput).length == TotpAuthenticator.DEFAULT_DIGITS,
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("confirm_enable_authenticator_button")
                            ) {
                                if (isMutating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Verifying…")
                                } else {
                                    Text("Verify & Enable Authenticator", fontWeight = FontWeight.Bold)
                                }
                            }

                            TextButton(
                                onClick = {
                                    pendingSetup = null
                                    enableCodeInput = ""
                                    feedbackMessage = null
                                },
                                enabled = !isMutating,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel Setup")
                            }
                        }
                    } else {
                        // ENABLED STATE
                        Surface(
                            color = EmeraldContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = if (status.requireOnLogin) {
                                    "Protected (${status.accountLabel.ifBlank { "Admin" }}): Signing in to the Admin Portal, removing active devices, Remove All Other Devices, changing the admin password, and disabling 2FA all require your live 6-digit Google Authenticator code."
                                } else {
                                    "Protected (${status.accountLabel.ifBlank { "Admin" }}): Removing active devices, Remove All Other Devices, changing the admin password, and disabling 2FA require your 6-digit Google Authenticator code. Login authentication code is currently turned off."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldAvailable,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            )
                        }

                        // Dedicated Enable / Disable option for Authentication Code on Login
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (status.requireOnLogin) {
                                    EmeraldAvailable.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = if (status.requireOnLogin) {
                                            EmeraldAvailable
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "Authentication Code for Login",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (status.requireOnLogin) {
                                                "Require a 6-digit Authenticator code when logging in to the Admin Portal."
                                            } else {
                                                "Admin login will not ask for a 6-digit code until enabled."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = status.requireOnLogin && !showDisableLoginCodeForm,
                                        enabled = !isMutating,
                                        onCheckedChange = { shouldEnable ->
                                            feedbackMessage = null
                                            if (shouldEnable) {
                                                showDisableLoginCodeForm = false
                                                disableLoginCodeInput = ""
                                                if (!status.requireOnLogin) {
                                                    onSetRequireAuthenticatorOnLogin(true, "") { result ->
                                                        result.onSuccess { okMsg ->
                                                            feedbackMessage = true to okMsg
                                                        }.onFailure { err ->
                                                            feedbackMessage = false to (
                                                                err.localizedMessage
                                                                    ?: "Unable to enable login authentication code"
                                                                )
                                                        }
                                                    }
                                                }
                                            } else if (status.requireOnLogin) {
                                                showDisableForm = false
                                                disableCodeInput = ""
                                                disableLoginCodeInput = ""
                                                showDisableLoginCodeForm = true
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedTrackColor = EmeraldAvailable
                                        ),
                                        modifier = Modifier.testTag("toggle_login_authenticator_code_switch")
                                    )
                                }

                                AnimatedVisibility(
                                    visible = showDisableLoginCodeForm && status.requireOnLogin,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Enter or paste your current 6-digit Google Authenticator code to disable login authentication code:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        TotpCodeInputField(
                                            value = disableLoginCodeInput,
                                            onValueChange = { disableLoginCodeInput = it },
                                            label = "Authenticator Code to Disable Login 2FA",
                                            enabled = !isMutating,
                                            testTag = "disable_login_authenticator_code_input"
                                        )
                                        Button(
                                            onClick = {
                                                feedbackMessage = null
                                                onSetRequireAuthenticatorOnLogin(false, disableLoginCodeInput) { result ->
                                                    result.onSuccess { okMsg ->
                                                        feedbackMessage = true to okMsg
                                                        disableLoginCodeInput = ""
                                                        showDisableLoginCodeForm = false
                                                    }.onFailure { err ->
                                                        feedbackMessage = false to (
                                                            err.localizedMessage
                                                                ?: "Incorrect Authenticator code"
                                                            )
                                                    }
                                                }
                                            },
                                            enabled = !isMutating &&
                                                TotpAuthenticator.sanitizeCodeInput(disableLoginCodeInput).length == TotpAuthenticator.DEFAULT_DIGITS,
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("confirm_disable_login_authenticator_code_button")
                                        ) {
                                            if (isMutating) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    color = MaterialTheme.colorScheme.onError,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Verifying…")
                                            } else {
                                                Text("Verify Code & Disable Login Code", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        TextButton(
                                            onClick = {
                                                showDisableLoginCodeForm = false
                                                disableLoginCodeInput = ""
                                                feedbackMessage = null
                                            },
                                            enabled = !isMutating,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Cancel")
                                        }
                                    }
                                }
                            }
                        }

                        if (!showDisableForm) {
                            OutlinedButton(
                                onClick = {
                                    feedbackMessage = null
                                    showDisableLoginCodeForm = false
                                    disableLoginCodeInput = ""
                                    disableCodeInput = ""
                                    showDisableForm = true
                                },
                                enabled = !isMutating,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("start_disable_authenticator_button")
                            ) {
                                Text("Disable Google Authenticator", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                text = "Paste the current 6-digit code from Google Authenticator first to disable 2FA protection:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            TotpCodeInputField(
                                value = disableCodeInput,
                                onValueChange = { disableCodeInput = it },
                                label = "Authenticator Code to Disable",
                                enabled = !isMutating,
                                testTag = "authenticator_disable_code_input"
                            )

                            Button(
                                onClick = {
                                    feedbackMessage = null
                                    onDisableAuthenticator(disableCodeInput) { result ->
                                        result.onSuccess { okMsg ->
                                            feedbackMessage = true to okMsg
                                            disableCodeInput = ""
                                            showDisableForm = false
                                        }.onFailure { err ->
                                            feedbackMessage = false to (err.localizedMessage ?: "Incorrect Authenticator code")
                                        }
                                    }
                                },
                                enabled = !isMutating && TotpAuthenticator.sanitizeCodeInput(disableCodeInput).length == TotpAuthenticator.DEFAULT_DIGITS,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("confirm_disable_authenticator_button")
                            ) {
                                if (isMutating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onError,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Verifying…")
                                } else {
                                    Text("Verify Code & Disable", fontWeight = FontWeight.Bold)
                                }
                            }

                            TextButton(
                                onClick = {
                                    showDisableForm = false
                                    disableCodeInput = ""
                                    feedbackMessage = null
                                },
                                enabled = !isMutating,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TotpCodeInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val sanitized = TotpAuthenticator.sanitizeCodeInput(raw).take(TotpAuthenticator.DEFAULT_DIGITS)
            onValueChange(sanitized)
        },
        label = { Text(label) },
        placeholder = { Text("123456") },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null
            )
        },
        trailingIcon = {
            IconButton(
                onClick = {
                    val pasted = readClipboardText(context)
                    val digits = TotpAuthenticator.sanitizeCodeInput(pasted)
                        .take(TotpAuthenticator.DEFAULT_DIGITS)
                    if (digits.isNotEmpty()) {
                        onValueChange(digits)
                    }
                },
                enabled = enabled,
                modifier = Modifier.testTag("${testTag}_paste_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Paste authenticator code"
                )
            }
        },
        supportingText = supportingText?.let { msg ->
            { Text(msg) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun readClipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    val clip = clipboard.primaryClip ?: return ""
    if (clip.itemCount <= 0) return ""
    return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
}
