package com.example.auth

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
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import android.content.Context
import coil.compose.AsyncImage
import com.example.R
import com.example.data.firebase.SecuritySessionRepository
import com.example.data.model.AdminAccessConfig
import com.example.data.network.CloudflareApiClient
import com.example.data.security.TotpAuthenticator
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.Locale

sealed interface AdminAuthorizationState {
    data object Checking : AdminAuthorizationState
    data class RequiresAuthenticator(
        val email: String,
        val secretBase32: String,
        val authTimeSeconds: Long
    ) : AdminAuthorizationState
    data class Authorized(
        val verifiedWithAuthenticator: Boolean = false
    ) : AdminAuthorizationState
    data class SessionExpired(val email: String) : AdminAuthorizationState
    data class Denied(val email: String) : AdminAuthorizationState
    data class Error(val email: String, val message: String) : AdminAuthorizationState
}

object AdminAuthorizationPolicy {
    fun isBootstrapOwnerEmail(email: String?): Boolean {
        val normalized = email?.trim()?.lowercase(Locale.US).orEmpty()
        return normalized == AdminAccessConfig.BOOTSTRAP_OWNER_EMAIL
    }

    fun isAuthorizedAdmin(
        userEmail: String?,
        isEmailVerified: Boolean,
        isExplicitUidAdmin: Boolean,
        allowlistedEmails: List<String>
    ): Boolean {
        if (!isEmailVerified) return false
        if (isBootstrapOwnerEmail(userEmail)) return true
        if (isExplicitUidAdmin) return true
        val normalized = userEmail?.trim()?.lowercase(Locale.US).orEmpty()
        if (normalized.isEmpty()) return false
        return AdminAccessConfig.normalizeEmails(allowlistedEmails).contains(normalized)
    }
}

fun observeAdminAuthorization(
    context: Context,
    apiClient: CloudflareApiClient,
    user: FirebaseUser
): Flow<AdminAuthorizationState> = flow {
    val rawEmail = user.email.orEmpty().trim()
    val verified = isUserVerified(user)

    if (!verified) {
        emit(AdminAuthorizationState.Denied(rawEmail))
        return@flow
    }

    emit(AdminAuthorizationState.Checking)

    // Ensure a valid Firebase Auth ID token before proceeding
    val tokenAuthTimeSeconds = runCatching {
        user.getIdToken(false).await().authTimestamp
    }.getOrDefault(0L)
    val userAuthTimeSeconds = tokenAuthTimeSeconds.takeIf { it > 0L }
        ?: ((user.metadata?.lastSignInTimestamp ?: 0L) / 1000L)

    if (AdminAuthorizationPolicy.isBootstrapOwnerEmail(rawEmail)) {
        val alreadyVerified = LoginTwoFactorStore.isSessionVerified(
            context = context,
            uid = user.uid,
            authTimeSeconds = userAuthTimeSeconds
        )
        val localAuth = SecuritySessionRepository.readLocalAuthenticatorConfig(context)
        val hasLoginAuthenticator = localAuth.enabled &&
            localAuth.requireOnLogin &&
            TotpAuthenticator.isValidSecretBase32(localAuth.secretBase32)

        if (LoginAuthenticatorPolicy.requiresAuthenticatorChallenge(
                userEmail = rawEmail,
                authenticatorEnabled = localAuth.enabled,
                secretBase32 = localAuth.secretBase32,
                alreadyVerifiedForSession = alreadyVerified,
                requireOnLogin = localAuth.requireOnLogin
            )
        ) {
            emit(
                AdminAuthorizationState.RequiresAuthenticator(
                    email = rawEmail,
                    secretBase32 = localAuth.secretBase32,
                    authTimeSeconds = userAuthTimeSeconds
                )
            )
            return@flow
        }

        emit(AdminAuthorizationState.Authorized(verifiedWithAuthenticator = hasLoginAuthenticator))
        return@flow
    }

    // Verify admin access directly against the Cloudflare Worker API using the Firebase ID token
    val checkResult = try {
        apiClient.verifyAdminAccess()
    } catch (e: CancellationException) {
        throw e
    }

    checkResult.fold(
        onSuccess = { isAuthorized ->
            if (isAuthorized) {
                emit(AdminAuthorizationState.Authorized(verifiedWithAuthenticator = false))
            } else {
                emit(AdminAuthorizationState.Denied(rawEmail))
            }
        },
        onFailure = { err ->
            emit(
                AdminAuthorizationState.Error(
                    email = rawEmail,
                    message = err.localizedMessage
                        ?: "Unable to verify admin approval right now. Check your internet connection and tap Re-check."
                )
            )
        }
    )
}

@Composable
fun AdminVerificationLoadingScreen(
    email: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(92.dp),
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
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp
            )
            Text(
                text = "Verifying admin approval…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (email.isNotBlank()) {
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun UnauthorizedAdminScreen(
    email: String,
    isNetworkError: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            border = BorderStroke(
                1.dp,
                if (isNetworkError) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            ),
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
                    color = if (isNetworkError) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = CircleShape
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isNetworkError) "VERIFICATION REQUIRED" else "ACCESS RESTRICTED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isNetworkError) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }

                Icon(
                    imageVector = if (isNetworkError) Icons.Default.WifiOff else Icons.Default.GppBad,
                    contentDescription = null,
                    tint = if (isNetworkError) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )

                Text(
                    text = if (isNetworkError) {
                        "Unable to Verify Admin Access"
                    } else {
                        "Admin Approval Required"
                    },
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
                    text = if (isNetworkError && !errorMessage.isNullOrBlank()) {
                        errorMessage
                    } else {
                        "This account is not approved to view vehicles or access the Alif Car Import Admin Portal. Sign in with ${AdminAccessConfig.BOOTSTRAP_OWNER_EMAIL} or ask the owner to approve your email under Admin Access."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onSignOut,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("unauthorized_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out & Switch Account", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onRetry,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("unauthorized_retry_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Re-check Approval", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
