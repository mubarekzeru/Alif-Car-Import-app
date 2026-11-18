package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.BrandingConfig
import com.example.data.model.ContactConfig
import com.example.data.model.HeroConfig
import com.example.data.model.ShowroomConfig
import com.example.data.model.TikTokSectionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowroomConfigSheet(
    config: ShowroomConfig,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ShowroomConfig) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isProcessingPhoto by remember { mutableStateOf(false) }

    var companyName by remember(config.updatedAt) { mutableStateOf(config.branding.companyName) }
    var logoUrl by remember(config.updatedAt) { mutableStateOf(config.branding.logoUrl) }
    var tagline by remember(config.updatedAt) { mutableStateOf(config.branding.tagline) }

    var heroKicker by remember(config.updatedAt) { mutableStateOf(config.hero.kicker) }
    var heroTitle by remember(config.updatedAt) { mutableStateOf(config.hero.title) }
    var heroDescription by remember(config.updatedAt) { mutableStateOf(config.hero.description) }
    var heroImageUrl by remember(config.updatedAt) { mutableStateOf(config.hero.imageUrl) }
    var heroCtaLabel by remember(config.updatedAt) { mutableStateOf(config.hero.ctaLabel) }

    var tiktokTitle by remember(config.updatedAt) { mutableStateOf(config.tiktok.title) }
    var tiktokDescription by remember(config.updatedAt) { mutableStateOf(config.tiktok.description) }
    var tiktokHandle by remember(config.updatedAt) { mutableStateOf(config.tiktok.handle) }
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

    val heroPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingPhoto = true
            scope.launch {
                val dataUrl = withContext(Dispatchers.IO) {
                    encodePickedPhotoAsDataUrl(context, uri)
                }
                if (dataUrl != null) {
                    heroImageUrl = dataUrl
                }
                isProcessingPhoto = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "WEBSITE SHOWROOM SYNC",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Branding, Hero & Contact",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            SectionHeader("BRANDING")
            OutlinedTextField(
                value = companyName,
                onValueChange = { if (it.length <= 120) companyName = it },
                label = { Text("Company Name *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = tagline,
                onValueChange = { if (it.length <= 240) tagline = it },
                label = { Text("Tagline") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = logoUrl,
                onValueChange = { if (it.length <= 550_000) logoUrl = it },
                label = { Text("Logo URL (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionHeader("WEBSITE HERO BANNER")
            OutlinedTextField(
                value = heroKicker,
                onValueChange = { if (it.length <= 120) heroKicker = it },
                label = { Text("Hero Kicker") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = heroTitle,
                onValueChange = { if (it.length <= 200) heroTitle = it },
                label = { Text("Hero Headline *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = heroDescription,
                onValueChange = { if (it.length <= 1200) heroDescription = it },
                label = { Text("Hero Description") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = heroCtaLabel,
                onValueChange = { if (it.length <= 80) heroCtaLabel = it },
                label = { Text("Button Label *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 64.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = decodeImageModel(heroImageUrl),
                        contentDescription = "Hero banner preview",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                OutlinedButton(
                    onClick = {
                        heroPhotoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick Hero Photo")
                }
            }
            if (!heroImageUrl.startsWith("data:image", ignoreCase = true)) {
                OutlinedTextField(
                    value = heroImageUrl,
                    onValueChange = { if (it.length <= 550_000) heroImageUrl = it },
                    label = { Text("Hero Image URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionHeader("TIKTOK SHOWCASE HEADER")
            OutlinedTextField(
                value = tiktokTitle,
                onValueChange = { if (it.length <= 160) tiktokTitle = it },
                label = { Text("Section Title *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = tiktokHandle,
                onValueChange = { if (it.length <= 80) tiktokHandle = it },
                label = { Text("TikTok Handle") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = tiktokProfileUrl,
                onValueChange = { if (it.length <= 500) tiktokProfileUrl = it },
                label = { Text("TikTok Profile URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = tiktokDescription,
                onValueChange = { if (it.length <= 1200) tiktokDescription = it },
                label = { Text("TikTok Description") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionHeader("CONTACT & SOCIAL LINKS")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 60) phone = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = whatsapp,
                    onValueChange = { if (it.length <= 200) whatsapp = it },
                    label = { Text("WhatsApp") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = telegram,
                    onValueChange = { if (it.length <= 200) telegram = it },
                    label = { Text("Telegram") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = instagram,
                    onValueChange = { if (it.length <= 200) instagram = it },
                    label = { Text("Instagram") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = facebook,
                    onValueChange = { if (it.length <= 200) facebook = it },
                    label = { Text("Facebook") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = xHandle,
                    onValueChange = { if (it.length <= 200) xHandle = it },
                    label = { Text("X (Twitter)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { if (it.length <= 254) email = it },
                label = { Text("Contact Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = addressLine,
                onValueChange = { if (it.length <= 240) addressLine = it },
                label = { Text("Address Line") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = cityCountry,
                    onValueChange = { if (it.length <= 160) cityCountry = it },
                    label = { Text("City / Country") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = hours,
                    onValueChange = { if (it.length <= 160) hours = it },
                    label = { Text("Showroom Hours") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
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

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
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
                            ShowroomConfig(
                                branding = BrandingConfig(
                                    companyName = companyName,
                                    logoUrl = logoUrl,
                                    tagline = tagline
                                ),
                                hero = HeroConfig(
                                    kicker = heroKicker,
                                    title = heroTitle,
                                    description = heroDescription,
                                    imageUrl = heroImageUrl,
                                    ctaLabel = heroCtaLabel
                                ),
                                tiktok = TikTokSectionConfig(
                                    title = tiktokTitle,
                                    description = tiktokDescription,
                                    profileUrl = tiktokProfileUrl,
                                    handle = tiktokHandle
                                ),
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
                    enabled = !isSaving && !isProcessingPhoto,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("save_showroom_config_button")
                ) {
                    if (isSaving || isProcessingPhoto) {
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

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}
