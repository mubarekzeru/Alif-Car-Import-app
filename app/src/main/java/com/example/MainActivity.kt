package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
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
import com.example.auth.AuthScreen
import com.example.auth.isUserVerified
import com.example.auth.signOut
import com.example.data.firebase.PortalRepository
import com.example.data.firebase.VehicleRepository
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.VehicleDetailScreen
import com.example.ui.theme.LotPulseTheme
import com.example.ui.viewmodel.InventoryUiState
import com.example.ui.viewmodel.InventoryViewModel
import com.example.ui.viewmodel.PortalViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_VEHICLE_DETAIL = "vehicle_detail/{vehicleId}"

private enum class AppRootStage {
    SPLASH,
    AUTH,
    AUTHENTICATED
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            LotPulseTheme {
                LotPulseApp()
            }
        }
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
fun LotPulseApp(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var authRefreshTick by remember { mutableIntStateOf(0) }
    var showLaunchSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(520)
        showLaunchSplash = false
    }

    val user = currentUser
    val verifiedUser = remember(user, authRefreshTick) {
        user?.takeIf { isUserVerified(it) }
    }

    val stage = when {
        showLaunchSplash -> AppRootStage.SPLASH
        verifiedUser == null -> AppRootStage.AUTH
        else -> AppRootStage.AUTHENTICATED
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
            AppRootStage.AUTH -> AuthScreen(onAuthSuccess = { authRefreshTick++ })
            AppRootStage.AUTHENTICATED -> {
                val activeUser = verifiedUser
                if (activeUser != null) {
                    key(activeUser.uid) {
                        AuthenticatedLotAdmin(
                            currentUserId = activeUser.uid,
                            userEmail = activeUser.email
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
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
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
                color = Color.White,
                border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                shadowElevation = 12.dp
            ) {
                AsyncImage(
                    model = R.drawable.img_alif_logo,
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
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
    currentUserId: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()

    val viewModel: InventoryViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                InventoryViewModel(
                    repository = VehicleRepository(db)
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
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                PortalViewModel(
                    repository = PortalRepository(db)
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = ROUTE_DASHBOARD,
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                initialOffsetX = { fullWidth -> fullWidth / 4 }
            ) + fadeIn(tween(220))
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = tween(240, easing = FastOutSlowInEasing),
                targetOffsetX = { fullWidth -> -fullWidth / 6 }
            ) + fadeOut(tween(180))
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = tween(240, easing = FastOutSlowInEasing),
                initialOffsetX = { fullWidth -> -fullWidth / 6 }
            ) + fadeIn(tween(200))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(240, easing = FastOutSlowInEasing),
                targetOffsetX = { fullWidth -> fullWidth / 4 }
            ) + fadeOut(tween(180))
        }
    ) {
        composable(ROUTE_DASHBOARD) {
            DashboardScreen(
                viewModel = viewModel,
                portalViewModel = portalViewModel,
                userEmail = userEmail,
                onOpenVehicleDetail = { vehicleId ->
                    navController.navigate("vehicle_detail/$vehicleId")
                },
                onSignOutClick = {
                    signOut(credentialManager = credentialManager, scope = scope)
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
                onQuickPriceClick = viewModel::openQuickPriceEdit,
                onStatusChange = viewModel::updateStatus,
                onToggleFeatured = viewModel::toggleFeatured,
                onEditClick = viewModel::openEditVehicleSheet,
                onDeleteClick = { target ->
                    viewModel.requestDeleteVehicle(target)
                    navController.popBackStack()
                }
            )
        }
    }
}
