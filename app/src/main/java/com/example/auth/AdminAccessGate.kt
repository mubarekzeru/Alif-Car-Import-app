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
import coil.compose.AsyncImage
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AdminAccessConfig
import android.os.Build
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

sealed interface AdminAuthorizationState {
    data object Checking : AdminAuthorizationState
    data object Authorized : AdminAuthorizationState
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
    db: FirebaseFirestore,
    user: FirebaseUser
): Flow<AdminAuthorizationState> = flow {
    val rawEmail = user.email.orEmpty().trim()
    val verified = isUserVerified(user)

    if (!verified) {
        emit(AdminAuthorizationState.Denied(rawEmail))
        return@flow
    }

    emit(AdminAuthorizationState.Checking)

    // Ensure fresh ID token & read real JWT auth_time (whole seconds) before checking Firestore rules
    val tokenAuthTimeSeconds = runCatching {
        user.getIdToken(true).await().authTimestamp
    }.recoverCatching {
        user.getIdToken(false).await().authTimestamp
    }.getOrDefault(0L)
    val userAuthTimeSeconds = tokenAuthTimeSeconds.takeIf { it > 0L }
        ?: ((user.metadata?.lastSignInTimestamp ?: 0L) / 1000L)

    // Server-side global sign-out check: if an admin triggered "Sign Out All Devices"
    // after this user signed in, expire this session immediately before showing any data.
    val cutoffSeconds = try {
        val secSnap = db.collection("config")
            .document("security")
            .get(Source.SERVER)
            .await()
        secSnap.getTimestamp("forceSignOutBefore")?.seconds
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        runCatching {
            db.collection("config")
                .document("security")
                .get(Source.CACHE)
                .await()
                .getTimestamp("forceSignOutBefore")
                ?.seconds
        }.getOrNull()
    }

    if (cutoffSeconds != null && userAuthTimeSeconds > 0L && userAuthTimeSeconds < cutoffSeconds) {
        emit(AdminAuthorizationState.SessionExpired(rawEmail))
        return@flow
    }

    if (AdminAuthorizationPolicy.isBootstrapOwnerEmail(rawEmail)) {
        emit(AdminAuthorizationState.Authorized)
        return@flow
    }

    val isExplicitUidAdmin = try {
        db.collection("admins")
            .document(user.uid)
            .get(Source.SERVER)
            .await()
            .exists()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val firestoreError = unwrapFirestoreException(e)
        if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            false
        } else {
            handleFirestoreError(e, OperationType.GET, "admins/${user.uid}")
            emit(
                AdminAuthorizationState.Error(
                    email = rawEmail,
                    message = "Unable to verify admin approval right now. Check your internet connection and tap Re-check."
                )
            )
            return@flow
        }
    }

    // Server-verified authorization check against config/adminAccess (rules enforce isAdmin())
    val initialApproved = try {
        val accessSnap = db.collection("config")
            .document("adminAccess")
            .get(Source.SERVER)
            .await()
        val emails = (accessSnap.data?.get("adminEmails") as? List<*>)
            ?.mapNotNull { it as? String }
            .orEmpty()
        AdminAuthorizationPolicy.isAuthorizedAdmin(
            userEmail = rawEmail,
            isEmailVerified = verified,
            isExplicitUidAdmin = isExplicitUidAdmin,
            allowlistedEmails = emails
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val firestoreError = unwrapFirestoreException(e)
        if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            false
        } else {
            handleFirestoreError(e, OperationType.GET, "config/adminAccess")
            emit(
                AdminAuthorizationState.Error(
                    email = rawEmail,
                    message = "Unable to verify admin approval right now. Check your internet connection and tap Re-check."
                )
            )
            return@flow
        }
    }

    if (!initialApproved) {
        reportUnauthorizedAdminLoginAttempt(db, rawEmail)
        emit(AdminAuthorizationState.Denied(rawEmail))
        return@flow
    }

    emit(AdminAuthorizationState.Authorized)

    // Keep observing config/adminAccess in real time so if the owner revokes this admin email,
    // access (including all vehicle listings) is revoked immediately.
    emitAll(
        db.collection("config")
            .document("adminAccess")
            .snapshots()
            .map { snapshot ->
                val emails = (snapshot.data?.get("adminEmails") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                val stillApproved = AdminAuthorizationPolicy.isAuthorizedAdmin(
                    userEmail = rawEmail,
                    isEmailVerified = verified,
                    isExplicitUidAdmin = isExplicitUidAdmin,
                    allowlistedEmails = emails
                )
                if (stillApproved) {
                    AdminAuthorizationState.Authorized
                } else {
                    AdminAuthorizationState.Denied(rawEmail)
                }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    emit(AdminAuthorizationState.Denied(rawEmail))
                } else {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, "config/adminAccess")
                    emit(
                        AdminAuthorizationState.Error(
                            email = rawEmail,
                            message = "Lost connection while verifying admin access. Tap Re-check to continue."
                        )
                    )
                }
            }
    )
}

private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? =
    generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()

private suspend fun reportUnauthorizedAdminLoginAttempt(
    db: FirebaseFirestore,
    email: String
) {
    runCatching {
        val attemptId = "att_app_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        val model = Build.MODEL.orEmpty().trim()
        val combined = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }
        val deviceName = "${combined.ifBlank { "Android Device" }} (Android ${Build.VERSION.RELEASE})".take(160)
        val country = Locale.getDefault().displayCountry.takeIf { it.isNotBlank() }
        val tzCity = TimeZone.getDefault().id.substringAfter('/', "").replace('_', ' ')
        val location = listOfNotNull(
            tzCity.takeIf { it.isNotBlank() },
            country
        ).joinToString(", ").ifBlank { TimeZone.getDefault().id }.take(160)

        val payload = mapOf(
            "id" to attemptId,
            "email" to email.trim().take(254),
            "deviceName" to deviceName,
            "platform" to "Mobile App Admin",
            "sourcePage" to "/admin/app-login",
            "location" to location,
            "status" to "denied",
            "browser" to "Mobile App",
            "reason" to "Unauthorized account attempted to sign into Admin App",
            "timestampMillis" to System.currentTimeMillis(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("adminLoginAttempts").document(attemptId).set(payload).await()
    }
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
