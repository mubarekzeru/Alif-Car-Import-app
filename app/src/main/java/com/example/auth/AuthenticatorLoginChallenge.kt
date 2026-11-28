package com.example.auth

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.security.TotpAuthenticator
import com.example.ui.components.TotpCodeInputField
import kotlinx.coroutines.delay

object LoginAuthenticatorPolicy {
    fun requiresAuthenticatorChallenge(
        userEmail: String?,
        authenticatorEnabled: Boolean,
        secretBase32: String,
        alreadyVerifiedForSession: Boolean,
        requireOnLogin: Boolean = true
    ): Boolean {
        if (!AdminAuthorizationPolicy.isBootstrapOwnerEmail(userEmail)) return false
        if (!authenticatorEnabled || !requireOnLogin) return false
        val cleanSecret = TotpAuthenticator.normalizeBase32(secretBase32)
        if (!TotpAuthenticator.isValidSecretBase32(cleanSecret)) return false
        return !alreadyVerifiedForSession
    }
}

object LoginTwoFactorStore {
    private const val PREFS_NAME = "alif_login_2fa_prefs"
    private const val KEY_VERIFIED_UID = "verified_uid"
    private const val KEY_VERIFIED_AUTH_TIME_SECONDS = "verified_auth_time_seconds"

    fun isSessionVerified(context: Context, uid: String, authTimeSeconds: Long): Boolean {
        if (uid.isBlank() || authTimeSeconds <= 0L) return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedUid = prefs.getString(KEY_VERIFIED_UID, null)
        val storedAuthTime = prefs.getLong(KEY_VERIFIED_AUTH_TIME_SECONDS, 0L)
        return storedUid == uid && storedAuthTime == authTimeSeconds
    }

    fun markSessionVerified(context: Context, uid: String, authTimeSeconds: Long) {
        if (uid.isBlank() || authTimeSeconds <= 0L) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_VERIFIED_UID, uid)
            .putLong(KEY_VERIFIED_AUTH_TIME_SECONDS, authTimeSeconds)
            .apply()
    }

    fun clear(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}

@Composable
fun AuthenticatorLoginChallengeScreen(
    email: String,
    secretBase32: String,
    onVerified: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var codeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var lockoutUntilMillis by remember { mutableLongStateOf(0L) }
    var remainingLockSeconds by remember { mutableIntStateOf(0) }

    BackHandler {
        onSignOut()
    }

    LaunchedEffect(lockoutUntilMillis) {
        while (lockoutUntilMillis > 0L) {
            val remainingMs = lockoutUntilMillis - System.currentTimeMillis()
            if (remainingMs <= 0L) {
                lockoutUntilMillis = 0L
                remainingLockSeconds = 0
                errorMessage = null
                break
            }
            remainingLockSeconds = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(1)
            delay(500L)
        }
    }

    fun submitVerification() {
        val now = System.currentTimeMillis()
        if (now < lockoutUntilMillis) {
            val secs = ((lockoutUntilMillis - now + 999L) / 1000L).coerceAtLeast(1L)
            errorMessage = "Too many incorrect codes. Wait ${secs}s and try a fresh code."
            return
        }
        val cleanCode = TotpAuthenticator.sanitizeCodeInput(codeInput)
        if (cleanCode.length != TotpAuthenticator.DEFAULT_DIGITS) {
            errorMessage = "Enter or paste the 6-digit code from Google Authenticator."
            return
        }
        val isValid = TotpAuthenticator.verifyCode(
            secretBase32 = secretBase32,
            userCode = cleanCode,
            timeMillis = now
        )
        if (isValid) {
            errorMessage = null
            failedAttempts = 0
            lockoutUntilMillis = 0L
            onVerified()
        } else {
            failedAttempts += 1
            if (failedAttempts >= 5) {
                lockoutUntilMillis = now + 30_000L
                failedAttempts = 0
                errorMessage = "Too many incorrect codes. Wait 30 seconds and paste a fresh code from Google Authenticator."
            } else {
                errorMessage = "Incorrect 6-digit code. Check Google Authenticator and try again."
            }
        }
    }

    val isLockedOut = remainingLockSeconds > 0
    val canSubmit = !isLockedOut &&
        TotpAuthenticator.sanitizeCodeInput(codeInput).length == TotpAuthenticator.DEFAULT_DIGITS

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(84.dp),
                    shape = CircleShape,
                    color = Color.White
                ) {
                    AsyncImage(
                        model = R.drawable.img_alif_logo,
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Fit
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = "2FA AUTHENTICATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )

                Text(
                    text = "Verify with Authenticator",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                if (email.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                Text(
                    text = "Enter or paste the live 6-digit code from Google Authenticator to complete your admin sign-in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                TotpCodeInputField(
                    value = codeInput,
                    onValueChange = {
                        codeInput = it
                        if (!isLockedOut) errorMessage = null
                    },
                    label = "Google Authenticator 6-Digit Code",
                    enabled = !isLockedOut,
                    isError = errorMessage != null,
                    supportingText = if (isLockedOut) {
                        "Cooldown active: ${remainingLockSeconds}s remaining"
                    } else {
                        "Tap the paste icon to paste directly from Google Authenticator"
                    },
                    testTag = "login_authenticator_code_input"
                )

                Button(
                    onClick = { submitVerification() },
                    enabled = canSubmit,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("login_authenticator_verify_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLockedOut) "Wait ${remainingLockSeconds}s" else "Verify & Sign In",
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onSignOut,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_authenticator_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out & Switch Account", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
