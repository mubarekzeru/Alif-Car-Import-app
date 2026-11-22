package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import android.util.Patterns
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val AUTH_TAG = "LotPulseAuth"

fun isUserVerified(user: FirebaseUser?): Boolean {
    if (user == null) return false
    return user.isEmailVerified ||
        user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onNeedsVerification: (String) -> Unit = {},
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    val existing = Firebase.auth.currentUser
    if (existing != null) {
        if (isUserVerified(existing)) {
            onAuthSuccess()
        } else {
            onNeedsVerification(existing.email.orEmpty())
        }
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val credential = credentialManager.getCredential(context, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val result = Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                runCatching { (result.user ?: Firebase.auth.currentUser)?.getIdToken(true)?.await() }
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val activity = context as? Activity
    if (activity == null) {
        onAuthError("Unable to launch sign-in without an Activity context.")
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found.")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val result = Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                runCatching { (result.user ?: Firebase.auth.currentUser)?.getIdToken(true)?.await() }
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type received.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign-in failed. Please try again.")
        }
    }
}

fun signInWithEmailPassword(
    email: String,
    password: String,
    onAuthSuccess: () -> Unit,
    onNeedsVerification: (String) -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope
) {
    val trimmedEmail = email.trim()
    if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
        onAuthError("Enter a valid admin email address.")
        return
    }
    if (password.isBlank()) {
        onAuthError("Enter your password.")
        return
    }

    scope.launch {
        try {
            val result = Firebase.auth.signInWithEmailAndPassword(trimmedEmail, password).await()
            val user = result.user ?: Firebase.auth.currentUser
            if (user == null) {
                onAuthError("Sign-in did not return an authenticated account.")
                return@launch
            }
            runCatching { user.reload().await() }
            if (isUserVerified(user)) {
                runCatching { user.getIdToken(true).await() }
                onAuthSuccess()
            } else {
                onNeedsVerification(user.email ?: trimmedEmail)
            }
        } catch (e: FirebaseAuthInvalidUserException) {
            Log.w(AUTH_TAG, "Email sign-in invalid user", e)
            onAuthError("No account found for $trimmedEmail. You can continue with Google instead.")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.w(AUTH_TAG, "Email sign-in invalid credentials", e)
            onAuthError("Incorrect email or password. Please check and try again.")
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Email sign-in failed", e)
            onAuthError(e.localizedMessage ?: "Email sign-in failed. Please try again.")
        }
    }
}

fun sendVerificationEmail(
    onSent: (String) -> Unit,
    onError: (String) -> Unit,
    scope: CoroutineScope
) {
    val user = Firebase.auth.currentUser
    if (user == null) {
        onError("No active session found. Please sign in again.")
        return
    }
    scope.launch {
        try {
            user.sendEmailVerification().await()
            onSent("Verification link sent to ${user.email.orEmpty()}. Open it, then tap I've Verified.")
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Failed to send verification email", e)
            onError(e.localizedMessage ?: "Unable to send verification email.")
        }
    }
}

fun refreshEmailVerification(
    onVerified: () -> Unit,
    onStillUnverified: () -> Unit,
    onError: (String) -> Unit,
    scope: CoroutineScope
) {
    val user = Firebase.auth.currentUser
    if (user == null) {
        onError("Session expired. Please sign in again.")
        return
    }
    scope.launch {
        try {
            user.reload().await()
            if (isUserVerified(user)) {
                user.getIdToken(true).await()
                onVerified()
            } else {
                onStillUnverified()
            }
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Failed to refresh verification status", e)
            onError(e.localizedMessage ?: "Could not verify account status.")
        }
    }
}

fun sendPasswordReset(
    email: String,
    onSent: (String) -> Unit,
    onError: (String) -> Unit,
    scope: CoroutineScope
) {
    val trimmedEmail = email.trim()
    if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
        onError("Enter your email address first so we know where to send the reset link.")
        return
    }
    scope.launch {
        try {
            Firebase.auth.sendPasswordResetEmail(trimmedEmail).await()
            onSent("Password reset link sent to $trimmedEmail.")
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Failed to send password reset email", e)
            onError(e.localizedMessage ?: "Unable to send password reset email.")
        }
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
