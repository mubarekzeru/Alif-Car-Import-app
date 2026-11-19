package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ContactConfig
import com.example.data.model.ShowroomConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSocialLocationScreen(
    config: ShowroomConfig,
    isSaving: Boolean,
    snackbarMessage: String?,
    onConsumeSnackbar: () -> Unit,
    onBack: () -> Unit,
    onSave: (ShowroomConfig) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeSnackbar()
    }

    var tiktokProfileUrl by remember(config.updatedAt) { mutableStateOf(config.tiktok.profileUrl) }
    var addressLine by remember(config.updatedAt) { mutableStateOf(config.contact.addressLine) }
    var cityCountry by remember(config.updatedAt) { mutableStateOf(config.contact.cityCountry) }
    var hours by remember(config.updatedAt) { mutableStateOf(config.contact.hours) }
    var phone by remember(config.updatedAt) { mutableStateOf(config.contact.phone) }
    var whatsapp by remember(config.updatedAt) { mutableStateOf(config.contact.whatsapp) }
    var telegram by remember(config.updatedAt) { mutableStateOf(config.contact.telegram) }
    var instagram by remember(config.updatedAt) { mutableStateOf(config.contact.instagram) }
    var facebook by remember(config.updatedAt) { mutableStateOf(config.contact.facebook) }
    var xHandle by remember(config.updatedAt) { mutableStateOf(config.contact.x) }
    var email by remember(config.updatedAt) { mutableStateOf(config.contact.email) }
    var mapsUrl by remember(config.updatedAt) { mutableStateOf(config.contact.mapsUrl) }
    var landmarkNote by remember(config.updatedAt) { mutableStateOf(config.contact.landmarkNote) }

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
                        enabled = !isSaving,
                        modifier = Modifier.testTag("contact_social_location_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to settings"
                        )
                    }
                },
                title = {
                    Text(
                        text = "Contact, Social Media & Location",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
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
                SectionHeader("SOCIAL MEDIA LINKS")
                OutlinedTextField(
                    value = tiktokProfileUrl,
                    onValueChange = { if (it.length <= 500) tiktokProfileUrl = it },
                    label = { Text("TikTok Link") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = whatsapp,
                        onValueChange = { if (it.length <= 200) whatsapp = it },
                        label = { Text("WhatsApp") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = telegram,
                        onValueChange = { if (it.length <= 200) telegram = it },
                        label = { Text("Telegram") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = instagram,
                        onValueChange = { if (it.length <= 200) instagram = it },
                        label = { Text("Instagram") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = facebook,
                        onValueChange = { if (it.length <= 200) facebook = it },
                        label = { Text("Facebook") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = xHandle,
                    onValueChange = { if (it.length <= 200) xHandle = it },
                    label = { Text("X (Twitter)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SectionHeader("CONTACT")
                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 60) phone = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { if (it.length <= 254) email = it },
                    label = { Text("Contact Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SectionHeader("LOCATION & WORKING DAYS")
                OutlinedTextField(
                    value = addressLine,
                    onValueChange = { if (it.length <= 240) addressLine = it },
                    label = { Text("Address Line") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cityCountry,
                    onValueChange = { if (it.length <= 160) cityCountry = it },
                    label = { Text("City / Country") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = landmarkNote,
                    onValueChange = { if (it.length <= 240) landmarkNote = it },
                    label = { Text("Landmark Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mapsUrl,
                    onValueChange = { if (it.length <= 500) mapsUrl = it },
                    label = { Text("Google Maps URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hours,
                    onValueChange = { if (it.length <= 160) hours = it },
                    label = { Text("Working Days & Hours") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onSave(
                                config.copy(
                                    tiktok = config.tiktok.copy(profileUrl = tiktokProfileUrl),
                                    contact = ContactConfig(
                                        addressLine = addressLine,
                                        cityCountry = cityCountry,
                                        hours = hours,
                                        phone = phone,
                                        whatsapp = whatsapp,
                                        telegram = telegram,
                                        instagram = instagram,
                                        facebook = facebook,
                                        x = xHandle,
                                        email = email,
                                        mapsUrl = mapsUrl,
                                        landmarkNote = landmarkNote
                                    )
                                )
                            )
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp)
                            .testTag("save_showroom_config_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing…")
                        } else {
                            Text("Save & Sync Website", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}
