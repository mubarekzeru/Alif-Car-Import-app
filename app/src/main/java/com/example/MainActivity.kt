package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.auth.AdminAuthorizationState
import com.example.auth.AdminVerificationLoadingScreen
import com.example.auth.AuthScreen
import com.example.auth.AuthenticatorLoginChallengeScreen
import com.example.auth.LoginTwoFactorStore
import com.example.auth.UnauthorizedAdminScreen
import com.example.auth.isUserVerified
import com.example.auth.observeAdminAuthorization
import com.example.auth.signOut
import com.example.data.firebase.PortalRepository
import com.example.data.firebase.SecuritySessionRepository
import com.example.data.firebase.VehicleRepository
import com.example.ui.components.AccountSettingsScreen
import com.example.ui.components.ActiveDevicesScreen
import com.example.ui.components.AddEditTikTokScreen
import com.example.ui.components.AddEditVehicleScreen
import com.example.ui.components.AdminAccessScreen
import com.example.ui.components.AppImageLoader
import com.example.ui.components.AuthenticatorSettingsScreen
import com.example.ui.components.BottomNavTab
import com.example.ui.components.ChangePasswordScreen
import com.example.ui.components.ContactSocialLocationScreen
import com.example.ui.components.InstantAlertsSettingsScreen
import com.example.ui.components.InteractionsScreen
import com.example.ui.components.NotificationCenterTab
import com.example.ui.components.NotificationsScreen
import com.example.ui.components.QuickPriceEditScreen
import com.example.ui.notifications.AdminAlertMonitorService
import com.example.ui.notifications.SecurityNotificationHelper
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.VehicleDetailScreen
import com.example.ui.theme.LotPulseTheme
import com.example.ui.viewmodel.InventoryUiState
import com.example.ui.viewmodel.InventoryViewModel
import com.example.ui.viewmodel.PortalViewModel
import com.example.data.network.CloudflareApiClient
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_VEHICLE_DETAIL = "vehicle_detail/{vehicleId}"
const val ROUTE_VEHICLE_FORM = "vehicle_form?vehicleId={vehicleId}"
const val ROUTE_QUICK_PRICE = "quick_price/{vehicleId}"
const val ROUTE_TIKTOK_FORM = "tiktok_form?postId={postId}"
const val ROUTE_NOTIFICATIONS = "notifications"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_ACTIVE_DEVICES = "active_devices"
const val ROUTE_INSTANT_ALERTS = "instant_alerts"
const val ROUTE_AUTHENTICATOR = "authenticator"
const val ROUTE_CHANGE_PASSWORD = "change_password"
const val ROUTE_CONTACT_SOCIAL_LOCATION = "contact_social_location"
const val ROUTE_ADMIN_ACCESS = "admin_access"
const val ROUTE_INTERACTIONS = "interactions"

private val LuxuryEaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private enum class AppRootStage {
    SPLASH,
    AUTH,
    VERIFYING_ADMIN,
    AUTHENTICATOR_CHALLENGE,
    UNAUTHORIZED,
    AUTHENTICATED
}

class MainActivity : ComponentActivity() {
    private val pendingNotificationTab = MutableStateFlow<NotificationCenterTab?>(null)
    private val pendingGrantCandidateEmail = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppImageLoader.install(applicationContext)
        CloudflareApiClient.getShared(applicationContext)
        SecurityNotificationHelper.ensureChannels(this)
        handleNotificationIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            val requestedTab by pendingNotificationTab.collectAsStateWithLifecycle()
            val requestedGrantEmail by pendingGrantCandidateEmail.collectAsStateWithLifecycle()
            LotPulseTheme {
                LotPulseApp(
                    requestedNotificationTab = requestedTab,
                    onConsumeNotificationTab = { pendingNotificationTab.value = null },
                    requestedGrantEmail = requestedGrantEmail,
                    onConsumeGrantEmail = { pendingGrantCandidateEmail.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val grantCandidate = intent.getStringExtra(SecurityNotificationHelper.EXTRA_GRANT_CANDIDATE_EMAIL)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (grantCandidate != null) {
            pendingGrantCandidateEmail.value = grantCandidate
            pendingNotificationTab.value = NotificationCenterTab.LOGIN_ALERTS
            intent.removeExtra(SecurityNotificationHelper.EXTRA_GRANT_CANDIDATE_EMAIL)
        }
        val rawTab = intent.getStringExtra(SecurityNotificationHelper.EXTRA_OPEN_NOTIFICATIONS_TAB)
            ?: return
        pendingNotificationTab.value = when (rawTab) {
            SecurityNotificationHelper.TAB_INQUIRIES -> NotificationCenterTab.INQUIRIES
            else -> NotificationCenterTab.LOGIN_ALERTS
        }
        intent.removeExtra(SecurityNotificationHelper.EXTRA_OPEN_NOTIFICATIONS_TAB)
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val authListener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    val tokenListener = FirebaseAuth.IdTokenListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(authListener)
    addIdTokenListener(tokenListener)
    awaitClose {
        removeAuthStateListener(authListener)
        removeIdTokenListener(tokenListener)
    }
}

@Composable
fun LotPulseApp(
    auth: FirebaseAuth = Firebase.auth,
    requestedNotificationTab: NotificationCenterTab? = null,
    onConsumeNotificationTab: () -> Unit = {},
    requestedGrantEmail: String? = null,
    onConsumeGrantEmail: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val apiClient = remember(context, auth) {
        CloudflareApiClient.getShared(context.applicationContext, auth)
    }

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var authRefreshTick by remember { mutableIntStateOf(0) }
    var adminCheckTick by remember { mutableIntStateOf(0) }
    var skipAutoSignIn by remember { mutableStateOf(false) }
    var showLaunchSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(520)
        showLaunchSplash = false
    }

    val user = currentUser
    val verifiedUser = remember(user, authRefreshTick) {
        user?.takeIf { isUserVerified(it) }
    }
    var passedAuthenticatorAuthTime by remember(verifiedUser?.uid, authRefreshTick) {
        mutableStateOf<Long?>(null)
    }

    val adminAuthState by produceState<AdminAuthorizationState>(
        initialValue = AdminAuthorizationState.Checking,
        key1 = verifiedUser?.uid,
        key2 = adminCheckTick
    ) {
        val active = verifiedUser
        if (active == null) {
            value = AdminAuthorizationState.Checking
            return@produceState
        }
        value = AdminAuthorizationState.Checking
        observeAdminAuthorization(context, apiClient, active).collect { state ->
            value = state
        }
    }

    val effectiveAdminState = when (val raw = adminAuthState) {
        is AdminAuthorizationState.RequiresAuthenticator -> {
            if (passedAuthenticatorAuthTime != null && passedAuthenticatorAuthTime == raw.authTimeSeconds) {
                AdminAuthorizationState.Authorized(verifiedWithAuthenticator = true)
            } else {
                raw
            }
        }
        else -> raw
    }

    LaunchedEffect(effectiveAdminState) {
        if (effectiveAdminState is AdminAuthorizationState.SessionExpired) {
            skipAutoSignIn = true
            LoginTwoFactorStore.clear(context)
            AdminAlertMonitorService.stop(context)
            signOut(credentialManager = credentialManager, scope = scope)
        }
    }

    val stage = when {
        showLaunchSplash -> AppRootStage.SPLASH
        verifiedUser == null -> AppRootStage.AUTH
        effectiveAdminState is AdminAuthorizationState.Checking ||
            effectiveAdminState is AdminAuthorizationState.SessionExpired -> AppRootStage.VERIFYING_ADMIN
        effectiveAdminState is AdminAuthorizationState.RequiresAuthenticator -> AppRootStage.AUTHENTICATOR_CHALLENGE
        effectiveAdminState is AdminAuthorizationState.Authorized -> AppRootStage.AUTHENTICATED
        else -> AppRootStage.UNAUTHORIZED
    }

    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            (fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.97f, animationSpec = tween(260, easing = FastOutSlowInEasing)))
                .togetherWith(fadeOut(tween(180)))
        },
        label = "app_root_transition"
    ) { targetStage ->
        when (targetStage) {
            AppRootStage.SPLASH -> BrandLaunchSplash()
            AppRootStage.AUTH -> AuthScreen(
                onAuthSuccess = {
                    skipAutoSignIn = false
                    passedAuthenticatorAuthTime = null
                    authRefreshTick++
                    adminCheckTick++
                },
                skipAutoSignIn = skipAutoSignIn
            )
            AppRootStage.VERIFYING_ADMIN -> AdminVerificationLoadingScreen(
                email = verifiedUser?.email.orEmpty()
            )
            AppRootStage.AUTHENTICATOR_CHALLENGE -> {
                val challenge = effectiveAdminState as? AdminAuthorizationState.RequiresAuthenticator
                val active = verifiedUser
                if (challenge != null && active != null) {
                    AuthenticatorLoginChallengeScreen(
                        email = challenge.email,
                        secretBase32 = challenge.secretBase32,
                        onVerified = {
                            LoginTwoFactorStore.markSessionVerified(
                                context = context,
                                uid = active.uid,
                                authTimeSeconds = challenge.authTimeSeconds
                            )
                            passedAuthenticatorAuthTime = challenge.authTimeSeconds
                        },
                        onSignOut = {
                            skipAutoSignIn = true
                            passedAuthenticatorAuthTime = null
                            LoginTwoFactorStore.clear(context)
                            AdminAlertMonitorService.stop(context)
                            signOut(credentialManager = credentialManager, scope = scope)
                        }
                    )
                }
            }
            AppRootStage.UNAUTHORIZED -> {
                val errorState = effectiveAdminState as? AdminAuthorizationState.Error
                val deniedState = effectiveAdminState as? AdminAuthorizationState.Denied
                UnauthorizedAdminScreen(
                    email = errorState?.email
                        ?: deniedState?.email
                        ?: verifiedUser?.email.orEmpty(),
                    isNetworkError = errorState != null,
                    errorMessage = errorState?.message,
                    onRetry = { adminCheckTick++ },
                    onSignOut = {
                        skipAutoSignIn = true
                        LoginTwoFactorStore.clear(context)
                        AdminAlertMonitorService.stop(context)
                        signOut(credentialManager = credentialManager, scope = scope)
                    }
                )
            }
            AppRootStage.AUTHENTICATED -> {
                val activeUser = verifiedUser
                val authInfo = effectiveAdminState as? AdminAuthorizationState.Authorized
                if (activeUser != null) {
                    key(activeUser.uid) {
                        AuthenticatedLotAdmin(
                            activeUser = activeUser,
                            verifiedWithAuthenticator = authInfo?.verifiedWithAuthenticator == true,
                            requestedNotificationTab = requestedNotificationTab,
                            onConsumeNotificationTab = onConsumeNotificationTab,
                            requestedGrantEmail = requestedGrantEmail,
                            onConsumeGrantEmail = onConsumeGrantEmail,
                            onWillSignOut = {
                                skipAutoSignIn = true
                                passedAuthenticatorAuthTime = null
                                LoginTwoFactorStore.clear(context)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandLaunchSplash() {
    val scale = remember { Animatable(0.84f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            alpha.animateTo(1f, animationSpec = tween(260, easing = FastOutSlowInEasing))
        }
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            Surface(
                modifier = Modifier.size(116.dp),
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
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            ) {
                Text(
                    text = "ADMIN PORTAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedLotAdmin(
    activeUser: FirebaseUser,
    verifiedWithAuthenticator: Boolean,
    requestedNotificationTab: NotificationCenterTab?,
    onConsumeNotificationTab: () -> Unit,
    requestedGrantEmail: String?,
    onConsumeGrantEmail: () -> Unit,
    onWillSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val currentUserId = activeUser.uid

    val viewModel: InventoryViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                InventoryViewModel(
                    repository = VehicleRepository(CloudflareApiClient.getShared(app.applicationContext))
                )
            }
        }
    )

    val portalViewModel: PortalViewModel = viewModel(
        key = "portal_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                PortalViewModel(
                    appContext = app.applicationContext,
                    repository = PortalRepository(
                        context = app.applicationContext,
                        apiClient = CloudflareApiClient.getShared(app.applicationContext)
                    ),
                    securityRepository = SecuritySessionRepository(app.applicationContext)
                )
            }
        }
    )

    LaunchedEffect(activeUser.uid, verifiedWithAuthenticator) {
        portalViewModel.registerAuthenticatedSession(
            user = activeUser,
            verifiedWithAuthenticator = verifiedWithAuthenticator
        )
        AdminAlertMonitorService.startIfEnabled(context)
    }

    // Periodically sync website inventory & photos into the app's local cache while in foreground
    LaunchedEffect(activeUser.uid) {
        while (true) {
            delay(45_000L)
            viewModel.refreshVehicles(showFeedback = false)
        }
    }

    LaunchedEffect(requestedNotificationTab) {
        if (requestedNotificationTab != null) {
            when (requestedNotificationTab) {
                NotificationCenterTab.INQUIRIES -> {
                    portalViewModel.selectBottomNavTab(BottomNavTab.INQUIRIES)
                    navController.popBackStack(ROUTE_DASHBOARD, inclusive = false)
                }
                NotificationCenterTab.LOGIN_ALERTS -> {
                    portalViewModel.selectNotificationTab(NotificationCenterTab.LOGIN_ALERTS)
                    navController.navigate(ROUTE_NOTIFICATIONS) {
                        launchSingleTop = true
                    }
                }
            }
            onConsumeNotificationTab()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activeUser.uid) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                portalViewModel.refreshSessionHeartbeat()
                portalViewModel.refreshPortalDataOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isSessionRevoked by portalViewModel.isCurrentSessionRevoked.collectAsStateWithLifecycle()
    LaunchedEffect(isSessionRevoked) {
        if (isSessionRevoked) {
            onWillSignOut()
            AdminAlertMonitorService.stop(context)
            portalViewModel.prepareSignOut()
            signOut(credentialManager = credentialManager, scope = scope)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isInventoryMutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val inventorySnackbar by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val isPortalMutating by portalViewModel.isMutating.collectAsStateWithLifecycle()
    val isChangingPassword by portalViewModel.isChangingPassword.collectAsStateWithLifecycle()
    val portalSnackbar by portalViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val showroomConfig by portalViewModel.showroomConfig.collectAsStateWithLifecycle()
    val tikTokPosts by portalViewModel.tikTokPosts.collectAsStateWithLifecycle()
    val inquiriesState by portalViewModel.inquiriesState.collectAsStateWithLifecycle()
    val adminAccessState by portalViewModel.adminAccessState.collectAsStateWithLifecycle()
    val loginAlerts by portalViewModel.loginAlerts.collectAsStateWithLifecycle()
    val readAlertIds by portalViewModel.readAlertIds.collectAsStateWithLifecycle()
    val notificationTab by portalViewModel.notificationTab.collectAsStateWithLifecycle()
    val sessions by portalViewModel.sessions.collectAsStateWithLifecycle()
    val currentLoginAtMillis by portalViewModel.currentLoginAtMillis.collectAsStateWithLifecycle()
    val securitySyncError by portalViewModel.securitySyncError.collectAsStateWithLifecycle()
    val isRefreshingSessions by portalViewModel.isRefreshingSessions.collectAsStateWithLifecycle()
    val isSigningOutAll by portalViewModel.isSigningOutAll.collectAsStateWithLifecycle()
    val isDeletingPreviousAlerts by portalViewModel.isDeletingPreviousAlerts.collectAsStateWithLifecycle()
    val authenticatorStatus by portalViewModel.authenticatorStatus.collectAsStateWithLifecycle()
    val isMutatingAuthenticator by portalViewModel.isMutatingAuthenticator.collectAsStateWithLifecycle()
    val websiteVisitorStats by portalViewModel.websiteVisitorStats.collectAsStateWithLifecycle()
    val isRefreshingVisitorStats by portalViewModel.isRefreshingVisitorStats.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = ROUTE_DASHBOARD,
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(340, easing = LuxuryEaseOut),
                initialOffsetX = { fullWidth -> fullWidth / 4 }
            ) + scaleIn(
                initialScale = 0.96f,
                animationSpec = tween(340, easing = LuxuryEaseOut)
            ) + fadeIn(tween(260, easing = LuxuryEaseOut))
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = tween(300, easing = LuxuryEaseOut),
                targetOffsetX = { fullWidth -> -fullWidth / 6 }
            ) + scaleOut(
                targetScale = 0.98f,
                animationSpec = tween(300, easing = LuxuryEaseOut)
            ) + fadeOut(tween(200))
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = tween(320, easing = LuxuryEaseOut),
                initialOffsetX = { fullWidth -> -fullWidth / 6 }
            ) + scaleIn(
                initialScale = 0.98f,
                animationSpec = tween(320, easing = LuxuryEaseOut)
            ) + fadeIn(tween(240, easing = LuxuryEaseOut))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(300, easing = LuxuryEaseOut),
                targetOffsetX = { fullWidth -> fullWidth / 4 }
            ) + scaleOut(
                targetScale = 0.96f,
                animationSpec = tween(300, easing = LuxuryEaseOut)
            ) + fadeOut(tween(200))
        }
    ) {
        composable(ROUTE_DASHBOARD) {
            DashboardScreen(
                viewModel = viewModel,
                portalViewModel = portalViewModel,
                userEmail = activeUser.email,
                onOpenVehicleDetail = { vehicleId ->
                    navController.navigate("vehicle_detail/$vehicleId") { launchSingleTop = true }
                },
                onOpenQuickPrice = { vehicleId ->
                    navController.navigate("quick_price/$vehicleId") { launchSingleTop = true }
                },
                onOpenAddVehicle = {
                    navController.navigate("vehicle_form") { launchSingleTop = true }
                },
                onOpenEditVehicle = { vehicleId ->
                    navController.navigate("vehicle_form?vehicleId=$vehicleId") { launchSingleTop = true }
                },
                onOpenAddTikTok = {
                    navController.navigate("tiktok_form") { launchSingleTop = true }
                },
                onOpenEditTikTok = { postId ->
                    navController.navigate("tiktok_form?postId=$postId") { launchSingleTop = true }
                },
                onOpenNotifications = {
                    navController.navigate(ROUTE_NOTIFICATIONS) { launchSingleTop = true }
                },
                onOpenSettings = {
                    navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                }
            )
        }

        composable(
            route = ROUTE_VEHICLE_DETAIL,
            arguments = listOf(navArgument("vehicleId") { type = NavType.StringType })
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getString("vehicleId").orEmpty()
            val vehicle = (uiState as? InventoryUiState.Success)
                ?.allVehicles
                ?.firstOrNull { it.id == vehicleId }

            VehicleDetailScreen(
                vehicle = vehicle,
                onBack = { navController.popBackStack() },
                onQuickPriceClick = { target ->
                    navController.navigate("quick_price/${target.id}") { launchSingleTop = true }
                },
                onStatusChange = viewModel::updateStatus,
                onToggleFeatured = viewModel::toggleFeatured,
                onEditClick = { target ->
                    navController.navigate("vehicle_form?vehicleId=${target.id}") { launchSingleTop = true }
                },
                onDeleteClick = { target ->
                    viewModel.requestDeleteVehicle(target)
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = ROUTE_VEHICLE_FORM,
            arguments = listOf(
                navArgument("vehicleId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getString("vehicleId")
            val editingVehicle = vehicleId?.let { id ->
                (uiState as? InventoryUiState.Success)?.allVehicles?.firstOrNull { it.id == id }
            }

            AddEditVehicleScreen(
                editingVehicle = editingVehicle,
                isSaving = isInventoryMutating,
                snackbarMessage = inventorySnackbar,
                onConsumeSnackbar = viewModel::consumeSnackbar,
                onBack = { navController.popBackStack() },
                onSubmit = { draft ->
                    viewModel.submitVehicleForm(editingVehicle?.id, draft) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(
            route = ROUTE_QUICK_PRICE,
            arguments = listOf(navArgument("vehicleId") { type = NavType.StringType })
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getString("vehicleId").orEmpty()
            val targetVehicle = (uiState as? InventoryUiState.Success)
                ?.allVehicles
                ?.firstOrNull { it.id == vehicleId }

            QuickPriceEditScreen(
                vehicle = targetVehicle,
                isSaving = isInventoryMutating,
                snackbarMessage = inventorySnackbar,
                onConsumeSnackbar = viewModel::consumeSnackbar,
                onBack = { navController.popBackStack() },
                onSavePrice = { vehicle, newPrice ->
                    viewModel.saveQuickPrice(vehicle, newPrice) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(
            route = ROUTE_TIKTOK_FORM,
            arguments = listOf(
                navArgument("postId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId")
            val editingPost = postId?.let { id ->
                tikTokPosts.firstOrNull { it.id == id }
            }

            AddEditTikTokScreen(
                editingPost = editingPost,
                defaultSortOrder = tikTokPosts.size.toLong(),
                isSaving = isPortalMutating,
                snackbarMessage = portalSnackbar,
                onConsumeSnackbar = portalViewModel::consumeSnackbar,
                onBack = { navController.popBackStack() },
                onSave = { id, draft ->
                    portalViewModel.saveTikTokPost(id, draft) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(ROUTE_NOTIFICATIONS) {
            NotificationsScreen(
                activeTab = notificationTab,
                alerts = loginAlerts,
                sessions = sessions,
                readAlertIds = readAlertIds,
                inquiriesState = inquiriesState,
                adminAccessState = adminAccessState,
                isMutatingAdminAccess = isPortalMutating,
                isDeletingPreviousAlerts = isDeletingPreviousAlerts,
                currentSessionId = portalViewModel.currentSessionId,
                currentSessionVerifiedWithAuthenticator = verifiedWithAuthenticator,
                preselectedGrantEmail = requestedGrantEmail,
                onConsumePreselectedGrantEmail = onConsumeGrantEmail,
                snackbarMessage = portalSnackbar,
                onConsumeSnackbar = portalViewModel::consumeSnackbar,
                onSelectTab = portalViewModel::selectNotificationTab,
                onMarkAllRead = portalViewModel::markAllLoginAlertsRead,
                onDeletePreviousAlerts = portalViewModel::deletePreviousLoginAlertsExceptCurrent,
                onConfirmItWasMe = portalViewModel::confirmLoginAlertIsMe,
                onRejectNotMe = portalViewModel::rejectLoginAlertNotMe,
                onGrantAdminAccess = { email ->
                    portalViewModel.grantAdminAccessFromBlockedAlert(email)
                },
                onRevokeAdminAccess = { email ->
                    portalViewModel.revokeAdminAccessFromAlert(email)
                },
                onRemoveFromLoggedIn = { email, sessionId ->
                    portalViewModel.removeUserFromLoggedIn(email, sessionId)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_SETTINGS) {
            AccountSettingsScreen(
                snackbarMessage = portalSnackbar,
                onConsumeSnackbar = portalViewModel::consumeSnackbar,
                onOpenActiveDevices = {
                    navController.navigate(ROUTE_ACTIVE_DEVICES) { launchSingleTop = true }
                },
                onOpenInstantAlerts = {
                    navController.navigate(ROUTE_INSTANT_ALERTS) { launchSingleTop = true }
                },
                onOpenAuthenticator = {
                    navController.navigate(ROUTE_AUTHENTICATOR) { launchSingleTop = true }
                },
                onOpenChangePassword = {
                    navController.navigate(ROUTE_CHANGE_PASSWORD) { launchSingleTop = true }
                },
                onOpenInteractions = {
                    navController.navigate(ROUTE_INTERACTIONS) { launchSingleTop = true }
                },
                onOpenAdminAccess = {
                    navController.navigate(ROUTE_ADMIN_ACCESS) { launchSingleTop = true }
                },
                onOpenContactSocialLocation = {
                    navController.navigate(ROUTE_CONTACT_SOCIAL_LOCATION) { launchSingleTop = true }
                },
                onSignOutClick = {
                    onWillSignOut()
                    AdminAlertMonitorService.stop(context)
                    scope.launch {
                        portalViewModel.prepareSignOut()
                        signOut(credentialManager = credentialManager, scope = scope)
                    }
                },
                onBack = { navController.popBackStack() },
                isSigningOutAll = isSigningOutAll
            )
        }

        composable(ROUTE_ACTIVE_DEVICES) {
            ActiveDevicesScreen(
                sessions = sessions,
                loginAtMillis = currentLoginAtMillis,
                snackbarMessage = portalSnackbar,
                onConsumeSnackbar = portalViewModel::consumeSnackbar,
                onDeactivateDevice = portalViewModel::deactivateOtherDevice,
                onBack = { navController.popBackStack() },
                securitySyncError = securitySyncError,
                isRefreshingSessions = isRefreshingSessions,
                onRefreshSessions = portalViewModel::refreshActiveSessions,
                isSigningOutAll = isSigningOutAll,
                onSignOutAllDevicesClick = { totpCode, onResult ->
                    scope.launch {
                        val result = portalViewModel.removeAllOtherActiveDevices(totpCode)
                        onResult(result)
                    }
                },
                authenticatorStatus = authenticatorStatus
            )
        }

        composable(ROUTE_INSTANT_ALERTS) {
            InstantAlertsSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_AUTHENTICATOR) {
            AuthenticatorSettingsScreen(
                authenticatorStatus = authenticatorStatus,
                isMutatingAuthenticator = isMutatingAuthenticator,
                snackbarMessage = portalSnackbar,
                onConsumeSnackbar = portalViewModel::consumeSnackbar,
                onCreatePendingAuthenticatorSetup = portalViewModel::createPendingAuthenticatorSetup,
                onEnableAuthenticator = portalViewModel::enableAuthenticator,
                onDisableAuthenticator = portalViewModel::disableAuthenticator,
                onSetRequireAuthenticatorOnLogin = portalViewModel::setRequireAuthenticatorOnLogin,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_CHANGE_PASSWORD) {
            ChangePasswordScreen(
                loginAtMillis = currentLoginAtMillis,
                isMutatingPassword = isChangingPassword,
                authenticatorStatus = authenticatorStatus,
                snackbarMessage = portalSnackbar,
                onConsumeSnackbar = portalViewModel::consumeSnackbar,
                onChangePassword = portalViewModel::changePassword,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_INTERACTIONS) {
            InteractionsScreen(
                websiteVisitorStats = websiteVisitorStats,
                inquiriesState = inquiriesState,
                isRefreshing = isRefreshingVisitorStats,
                onRefresh = portalViewModel::refreshWebsiteVisitorStats,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_CONTACT_SOCIAL_LOCATION) {
            ContactSocialLocationScreen(
                config = showroomConfig,
                isSaving = isPortalMutating,
                snackbarMessage = portalSnackbar,
                onConsumeSnackbar = portalViewModel::consumeSnackbar,
                onBack = { navController.popBackStack() },
                onSave = { updated ->
                    portalViewModel.saveShowroomConfig(updated) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(ROUTE_ADMIN_ACCESS) {
            AdminAccessScreen(
                state = adminAccessState,
                isSaving = isPortalMutating,
                snackbarMessage = portalSnackbar,
                onConsumeSnackbar = portalViewModel::consumeSnackbar,
                onBack = { navController.popBackStack() },
                onSaveEmails = { emails ->
                    portalViewModel.saveAdminEmails(emails) {
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}
