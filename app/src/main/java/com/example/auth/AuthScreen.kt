package com.example.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.R

private enum class AuthScreenMode {
    SIGN_IN,
    VERIFY_EMAIL
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    skipAutoSignIn: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isCheckingSession by remember { mutableStateOf(true) }
    var isSigningIn by remember { mutableStateOf(false) }
    var activeMode by rememberSaveable { mutableStateOf(AuthScreenMode.SIGN_IN) }
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var pendingVerifyEmail by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var cardVisible by remember { mutableStateOf(false) }

    BackHandler(enabled = activeMode == AuthScreenMode.VERIFY_EMAIL && !isSigningIn) {
        errorMessage = null
        infoMessage = null
        LoginTwoFactorStore.clear(context)
        signOut(
            credentialManager = credentialManager,
            scope = scope,
            onSignOutComplete = {
                activeMode = AuthScreenMode.SIGN_IN
            }
        )
    }

    LaunchedEffect(Unit) {
        cardVisible = true
        if (skipAutoSignIn) {
            isCheckingSession = false
            return@LaunchedEffect
        }
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingSession = false
                onAuthSuccess()
            },
            onNeedsVerification = { email ->
                isCheckingSession = false
                pendingVerifyEmail = email
                activeMode = AuthScreenMode.VERIFY_EMAIL
            },
            onUnauthenticated = {
                isCheckingSession = false
            },
            scope = scope
        )
    }

    fun triggerGoogleSignIn() {
        errorMessage = null
        infoMessage = null
        isSigningIn = true
        LoginTwoFactorStore.clear(context)
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isSigningIn = false
                onAuthSuccess()
            },
            onAuthError = { msg ->
                isSigningIn = false
                errorMessage = msg
            },
            scope = scope,
            onAuthCancelled = {
                isSigningIn = false
            }
        )
    }

    fun triggerEmailSignIn() {
        focusManager.clearFocus()
        errorMessage = null
        infoMessage = null
        isSigningIn = true
        LoginTwoFactorStore.clear(context)
        signInWithEmailPassword(
            context = context,
            email = emailInput,
            password = passwordInput,
            onAuthSuccess = {
                isSigningIn = false
                onAuthSuccess()
            },
            onNeedsVerification = { email ->
                isSigningIn = false
                pendingVerifyEmail = email
                activeMode = AuthScreenMode.VERIFY_EMAIL
            },
            onAuthError = { msg ->
                isSigningIn = false
                errorMessage = msg
            },
            scope = scope
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = cardVisible,
            enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.96f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(96.dp),
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "ADMIN CONTROL PANEL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.auth_headline),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.auth_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp),
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

                    AnimatedVisibility(
                        visible = infoMessage != null,
                        enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = infoMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = activeMode,
                        transitionSpec = {
                            (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                                expandVertically(spring(stiffness = Spring.StiffnessMediumLow)))
                                .togetherWith(fadeOut(tween(150)))
                        },
                        label = "auth_mode_transition"
                    ) { mode ->
                        when (mode) {
                            AuthScreenMode.SIGN_IN -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = emailInput,
                                        onValueChange = { emailInput = it },
                                        label = { Text("Enter email") },
                                        placeholder = { Text("Enter email") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Email, contentDescription = null)
                                        },
                                        singleLine = true,
                                        enabled = !isSigningIn && !isCheckingSession,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Email,
                                            imeAction = ImeAction.Next
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("email_input")
                                    )

                                    OutlinedTextField(
                                        value = passwordInput,
                                        onValueChange = { passwordInput = it },
                                        label = { Text("Password") },
                                        placeholder = { Text("Enter password") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Lock, contentDescription = null)
                                        },
                                        singleLine = true,
                                        enabled = !isSigningIn && !isCheckingSession,
                                        visualTransformation = if (passwordVisible) {
                                            VisualTransformation.None
                                        } else {
                                            PasswordVisualTransformation()
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { passwordVisible = !passwordVisible }
                                            ) {
                                                Icon(
                                                    imageVector = if (passwordVisible) {
                                                        Icons.Default.VisibilityOff
                                                    } else {
                                                        Icons.Default.Visibility
                                                    },
                                                    contentDescription = if (passwordVisible) {
                                                        "Hide password"
                                                    } else {
                                                        "Show password"
                                                    }
                                                )
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = { if (!isSigningIn && !isCheckingSession) triggerEmailSignIn() }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("password_input")
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                errorMessage = null
                                                infoMessage = null
                                                sendPasswordReset(
                                                    email = emailInput,
                                                    onSent = { msg -> infoMessage = msg },
                                                    onError = { err -> errorMessage = err },
                                                    scope = scope
                                                )
                                            },
                                            enabled = !isSigningIn && !isCheckingSession
                                        ) {
                                            Text("Forgot password?")
                                        }
                                    }

                                    Button(
                                        onClick = { triggerEmailSignIn() },
                                        enabled = !isSigningIn && !isCheckingSession,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .testTag("email_sign_in_submit_button"),
                                        shape = MaterialTheme.shapes.medium,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        if (isSigningIn || isCheckingSession) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.5.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = if (isCheckingSession) {
                                                    stringResource(R.string.auth_checking_session)
                                                } else {
                                                    "Signing in…"
                                                },
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        } else {
                                            Text(
                                                text = "Sign In",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        HorizontalDivider(
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        Text(
                                            text = "OR",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { triggerGoogleSignIn() },
                                        enabled = !isSigningIn && !isCheckingSession,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .testTag("google_sign_in_button")
                                    ) {
                                        Text(
                                            text = stringResource(R.string.auth_sign_in_google),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            AuthScreenMode.VERIFY_EMAIL -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MarkEmailRead,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = "Verify your email to access live Firestore",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Security rules require email verification for ${pendingVerifyEmail.ifBlank { "your account" }}. Verify via the link sent to your inbox, then tap Continue.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = {
                                            errorMessage = null
                                            infoMessage = null
                                            isSigningIn = true
                                            refreshEmailVerification(
                                                onVerified = {
                                                    isSigningIn = false
                                                    onAuthSuccess()
                                                },
                                                onStillUnverified = {
                                                    isSigningIn = false
                                                    errorMessage = "Email is still unverified. Click the link in your inbox, then try again."
                                                },
                                                onError = { err ->
                                                    isSigningIn = false
                                                    errorMessage = err
                                                },
                                                scope = scope
                                            )
                                        },
                                        enabled = !isSigningIn,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Text("I've Verified — Continue", fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            errorMessage = null
                                            infoMessage = null
                                            sendVerificationEmail(
                                                onSent = { msg -> infoMessage = msg },
                                                onError = { err -> errorMessage = err },
                                                scope = scope
                                            )
                                        },
                                        enabled = !isSigningIn,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        Text("Resend Verification Email")
                                    }
                                    TextButton(
                                        onClick = {
                                            errorMessage = null
                                            infoMessage = null
                                            LoginTwoFactorStore.clear(context)
                                            signOut(
                                                credentialManager = credentialManager,
                                                scope = scope,
                                                onSignOutComplete = {
                                                    activeMode = AuthScreenMode.SIGN_IN
                                                }
                                            )
                                        }
                                    ) {
                                        Text("Back to sign-in")
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
