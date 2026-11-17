package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.data.model.StudioPreset
import com.example.data.model.Vehicle
import com.example.data.model.VehicleDraft
import com.example.data.model.VehicleStatus
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditVehicleSheet(
    editingVehicle: Vehicle?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (VehicleDraft) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var yearInput by remember(editingVehicle?.id) {
        mutableStateOf((editingVehicle?.year ?: 2024L).toString())
    }
    var makeInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.make ?: "")
    }
    var modelInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.model ?: "")
    }
    var titleInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.title ?: "")
    }
    var priceInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.price?.toString() ?: "")
    }
    var originalPriceInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.originalPrice?.toString() ?: "")
    }
    var mileageInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.mileage ?: "0 km")
    }
    var transmissionInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.transmission?.ifBlank { "Automatic" } ?: "Automatic")
    }
    var fuelTypeInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.fuelType?.ifBlank { "Petrol" } ?: "Petrol")
    }
    var colorInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.color ?: "")
    }
    var originInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.origin ?: "")
    }
    var selectedStatus by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.statusEnum ?: VehicleStatus.AVAILABLE)
    }
    var featured by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.featured ?: false)
    }
    var selectedPreset by remember(editingVehicle?.id) {
        mutableStateOf(StudioPreset.SUV)
    }
    var imageUrlInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.imageUrl ?: "")
    }
    var descriptionInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.description ?: "")
    }
    var tiktokUrlInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.tiktokUrl ?: "")
    }
    var attemptedSubmit by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            encodePickedPhotoAsDataUrl(context, uri)?.let { dataUrl ->
                imageUrlInput = dataUrl
            }
        }
    }

    val parsedYear = yearInput.toLongOrNull()
    val parsedPrice = priceInput.toLongOrNull()
    val parsedOriginalPrice = originalPriceInput.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()

    val yearValid = parsedYear != null && parsedYear in 1950L..2100L
    val makeValid = makeInput.trim().length in 1..80
    val modelValid = modelInput.trim().length in 1..120
    val priceValid = parsedPrice != null && parsedPrice in 0L..100_000_000L
    val originalPriceValid = originalPriceInput.trim().isEmpty() ||
        (parsedOriginalPrice != null && parsedOriginalPrice in 0L..100_000_000L)
    val formValid = yearValid && makeValid && modelValid && priceValid && originalPriceValid

    val previewModel: Any = remember(imageUrlInput, selectedPreset) {
        decodeImageModel(imageUrlInput, fallbackPreset = selectedPreset)
    }
    val hasPickedDataUrl = imageUrlInput.startsWith("data:image", ignoreCase = true)

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (editingVehicle == null) "QUICK VEHICLE INTAKE" else "EDIT VEHICLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (editingVehicle == null) {
                        "Publish Vehicle to Portal"
                    } else {
                        editingVehicle.displayTitle
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Photo Picker + Preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 88.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = previewModel,
                        contentDescription = "Vehicle photo preview",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (imageUrlInput.isNotBlank()) {
                        IconButton(
                            onClick = { imageUrlInput = "" },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear image",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("pick_vehicle_photo_button"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (hasPickedDataUrl) "Change Photo" else "Pick Photo")
                    }
                    Text(
                        text = if (hasPickedDataUrl) {
                            "Photo compressed for web & mobile sync."
                        } else {
                            "Pick a photo, paste URL, or use studio ${selectedPreset.label}."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (imageUrlInput.isBlank()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { selectedPreset = preset },
                            label = { Text(preset.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.height(40.dp)
                        )
                    }
                }
            }

            if (!hasPickedDataUrl) {
                OutlinedTextField(
                    value = imageUrlInput,
                    onValueChange = { if (it.length <= 550_000) imageUrlInput = it },
                    label = { Text("Image URL (optional if using studio photo)") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vehicle_photo_url_input")
                )
            }

            // Year + Make
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = yearInput,
                    onValueChange = { if (it.length <= 4) yearInput = it.filter(Char::isDigit) },
                    label = { Text("Year *") },
                    isError = attemptedSubmit && !yearValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(0.35f)
                        .testTag("vehicle_year_input")
                )
                OutlinedTextField(
                    value = makeInput,
                    onValueChange = { if (it.length <= 80) makeInput = it },
                    label = { Text("Make *") },
                    placeholder = { Text("Lexus") },
                    isError = attemptedSubmit && !makeValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .weight(0.65f)
                        .testTag("vehicle_make_input")
                )
            }

            // Model + Display Title
            OutlinedTextField(
                value = modelInput,
                onValueChange = { if (it.length <= 120) modelInput = it },
                label = { Text("Model *") },
                placeholder = { Text("RX 350") },
                isError = attemptedSubmit && !modelValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vehicle_model_input")
            )

            OutlinedTextField(
                value = titleInput,
                onValueChange = { if (it.length <= 200) titleInput = it },
                label = { Text("Listing Title (auto-filled if blank)") },
                placeholder = { Text("2024 Lexus RX 350 F Sport") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vehicle_title_input")
            )

            // Price + Original Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { next ->
                        val digits = next.filter(Char::isDigit)
                        if (digits.length <= 9) priceInput = digits
                    },
                    label = { Text("Price *") },
                    prefix = { Text("$", fontFamily = JetBrainsMonoFontFamily) },
                    isError = attemptedSubmit && !priceValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vehicle_price_input")
                )
                OutlinedTextField(
                    value = originalPriceInput,
                    onValueChange = { next ->
                        val digits = next.filter(Char::isDigit)
                        if (digits.length <= 9) originalPriceInput = digits
                    },
                    label = { Text("Original Price") },
                    prefix = { Text("$", fontFamily = JetBrainsMonoFontFamily) },
                    isError = attemptedSubmit && !originalPriceValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vehicle_original_price_input")
                )
            }

            // Mileage + Origin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = mileageInput,
                    onValueChange = { if (it.length <= 60) mileageInput = it },
                    label = { Text("Mileage") },
                    placeholder = { Text("12,400 km") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vehicle_mileage_input")
                )
                OutlinedTextField(
                    value = originInput,
                    onValueChange = { if (it.length <= 100) originInput = it },
                    label = { Text("Origin") },
                    placeholder = { Text("Dubai / Japan") },
                    singleLine = true,
                    keyboardOptions = KeyboardCapitalization.Words.let {
                        KeyboardOptions(capitalization = it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vehicle_origin_input")
                )
            }

            // Transmission + Fuel Type + Color
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = transmissionInput,
                    onValueChange = { if (it.length <= 80) transmissionInput = it },
                    label = { Text("Transmission") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = fuelTypeInput,
                    onValueChange = { if (it.length <= 80) fuelTypeInput = it },
                    label = { Text("Fuel Type") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = colorInput,
                onValueChange = { if (it.length <= 80) colorInput = it },
                label = { Text("Exterior Color") },
                placeholder = { Text("Pearl White") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )

            // Status Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Lot Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VehicleStatus.entries.forEach { status ->
                        val colors = status.colors()
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            label = { Text(status.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.background,
                                selectedLabelColor = colors.foreground
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        )
                    }
                }
            }

            // Featured Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Featured Listing",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Highlight this vehicle on the portal showcase",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = featured,
                    onCheckedChange = { featured = it }
                )
            }

            OutlinedTextField(
                value = descriptionInput,
                onValueChange = { if (it.length <= 4000) descriptionInput = it },
                label = { Text("Description (auto-generated if blank)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vehicle_description_input")
            )

            OutlinedTextField(
                value = tiktokUrlInput,
                onValueChange = { if (it.length <= 500) tiktokUrlInput = it },
                label = { Text("TikTok Video URL (optional)") },
                placeholder = { Text("https://www.tiktok.com/…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            if (attemptedSubmit && !formValid) {
                Text(
                    text = "Please provide a valid Year (1950–2100), Make, Model, and Price.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        attemptedSubmit = true
                        if (formValid && parsedYear != null && parsedPrice != null) {
                            val resolvedImageUrl = imageUrlInput.trim().ifEmpty {
                                encodeDrawableAsDataUrl(context, selectedPreset.studioDrawableRes())
                                    ?: "https://alifcarimport.com/placeholder.jpg"
                            }
                            onSubmit(
                                VehicleDraft(
                                    title = titleInput.trim(),
                                    make = makeInput.trim(),
                                    model = modelInput.trim(),
                                    year = parsedYear,
                                    price = parsedPrice,
                                    originalPrice = parsedOriginalPrice,
                                    mileage = mileageInput.trim(),
                                    transmission = transmissionInput.trim(),
                                    fuelType = fuelTypeInput.trim(),
                                    color = colorInput.trim(),
                                    origin = originInput.trim(),
                                    imageUrl = resolvedImageUrl,
                                    galleryImages = editingVehicle?.galleryImages ?: emptyList(),
                                    description = descriptionInput.trim(),
                                    tiktokUrl = tiktokUrlInput.trim(),
                                    status = selectedStatus,
                                    featured = featured
                                )
                            )
                        }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(52.dp)
                        .testTag("submit_vehicle_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving…")
                    } else {
                        Text(
                            text = if (editingVehicle == null) "Publish Vehicle" else "Save Changes",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
