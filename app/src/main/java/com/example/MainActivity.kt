package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
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
import com.example.auth.AuthScreen
import com.example.auth.signOut
import com.example.data.firebase.VehicleRepository
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.VehicleDetailScreen
import com.example.ui.theme.LotPulseTheme
import com.example.ui.viewmodel.InventoryUiState
import com.example.ui.viewmodel.InventoryViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_VEHICLE_DETAIL = "vehicle_detail/{vehicleId}"

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
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun LotPulseApp(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedLotAdmin(
                currentUserId = user.uid,
                userEmail = user.email
            )
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

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = ROUTE_DASHBOARD
    ) {
        composable(ROUTE_DASHBOARD) {
            DashboardScreen(
                viewModel = viewModel,
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
