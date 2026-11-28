package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import java.util.Calendar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.Vehicle
import com.example.data.model.VehicleDraft
import com.example.data.model.VehicleStatus
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditVehicleScreen(
    editingVehicle: Vehicle?,
    isSaving: Boolean,
    snackbarMessage: String?,
    onConsumeSnackbar: () -> Unit,
    onBack: () -> Unit,
    onSubmit: (VehicleDraft) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeSnackbar()
    }

    var isProcessingPhoto by remember { mutableStateOf(false) }

    val currentYear = remember {
        Calendar.getInstance().get(Calendar.YEAR).toLong().coerceIn(1950L, 2100L)
    }

    var yearInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.year?.takeIf { it in 1950L..2100L }?.toString() ?: "")
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
        mutableStateOf(editingVehicle?.price?.takeIf { it > 0L }?.toString() ?: "")
    }
    var originalPriceInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.originalPrice?.takeIf { it > 0L }?.toString() ?: "")
    }
    var mileageInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.mileage ?: "")
    }
    var transmissionInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.transmission ?: "")
    }
    var fuelTypeInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.fuelType ?: "")
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
    var imageUrlInput by remember(editingVehicle?.id) {
        val existingUrl = editingVehicle?.imageUrl.orEmpty()
        mutableStateOf(
            if (existingUrl.equals("https://alifcarimport.com/placeholder.jpg", ignoreCase = true)) {
                ""
            } else {
                existingUrl
            }
        )
    }
    var galleryImagesInput by remember(editingVehicle?.id) {
        mutableStateOf(editingVehicle?.galleryImages ?: emptyList())
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
            isProcessingPhoto = true
            scope.launch {
                val dataUrl = withContext(Dispatchers.IO) {
                    encodePickedPhotoAsDataUrl(context, uri)
                }
                if (dataUrl != null) {
                    imageUrlInput = dataUrl
                }
                isProcessingPhoto = false
            }
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            isProcessingPhoto = true
            scope.launch {
                val remaining = (10 - galleryImagesInput.size).coerceAtLeast(0)
                val encoded = withContext(Dispatchers.IO) {
                    coroutineScope {
                        uris.take(remaining).map { uri ->
                            async { encodeGalleryPhotoAsDataUrl(context, uri) }
                        }.awaitAll().filterNotNull()
                    }
                }
                if (encoded.isNotEmpty()) {
                    galleryImagesInput = (galleryImagesInput + encoded).take(10)
                }
                isProcessingPhoto = false
            }
        }
    }

    val trimmedYear = yearInput.trim()
    val parsedYear = trimmedYear.takeIf { it.isNotEmpty() }?.toLongOrNull()
    val resolvedYear = parsedYear ?: currentYear

    val trimmedPrice = priceInput.trim()
    val parsedPrice = trimmedPrice.takeIf { it.isNotEmpty() }?.toLongOrNull()
    val resolvedPrice = parsedPrice ?: 0L

    val parsedOriginalPrice = originalPriceInput.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()

    val yearValid = trimmedYear.isEmpty() || (parsedYear != null && parsedYear in 1950L..2100L)
    val priceValid = trimmedPrice.isEmpty() || (parsedPrice != null && parsedPrice in 0L..100_000_000L)
    val originalPriceValid = originalPriceInput.trim().isEmpty() ||
        (parsedOriginalPrice != null && parsedOriginalPrice in 0L..100_000_000L)
    val formValid = yearValid && priceValid && originalPriceValid

    val previewCandidates = remember(imageUrlInput) {
        resolveCandidateImageUrls(imageUrlInput)
    }
    val hasPickedDataUrl = imageUrlInput.startsWith("data:image", ignoreCase = true)

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
                        modifier = Modifier.testTag("close_vehicle_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = if (editingVehicle == null) {
                                "Add Vehicle"
                            } else {
                                "Edit Car"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (editingVehicle != null) {
                            Text(
                                text = editingVehicle.displayTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        ResilientAppImage(
                            candidateUrls = previewCandidates,
                            contentDescription = "Vehicle photo preview",
                            cacheKeyPrefix = "veh_edit_preview_${editingVehicle?.id ?: "new"}",
                            width = 360,
                            height = 264,
                            crossfadeMillis = 160,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "No vehicle photo selected",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
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
                                "Photo ready — uploads to website & caches in app."
                            } else {
                                "Pick a photo from your device or paste an image URL."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!hasPickedDataUrl) {
                    OutlinedTextField(
                        value = imageUrlInput,
                        onValueChange = { if (it.length <= 800_000) imageUrlInput = it },
                        label = { Text("Image URL") },
                        placeholder = { Text("https://…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vehicle_photo_url_input")
                    )
                }

                // Additional Gallery Photos (up to 10)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gallery Photos (${galleryImagesInput.size}/10)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (galleryImagesInput.size < 10) {
                            OutlinedButton(
                                onClick = {
                                    galleryPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("add_gallery_photos_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Photos", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (galleryImagesInput.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(galleryImagesInput) { index, img ->
                                Box(
                                    modifier = Modifier
                                        .size(width = 84.dp, height = 64.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    val galleryCandidates = remember(img) {
                                        resolveCandidateImageUrls(img)
                                    }
                                    ResilientAppImage(
                                        candidateUrls = galleryCandidates,
                                        contentDescription = "Gallery photo ${index + 1}",
                                        cacheKeyPrefix = "veh_edit_gal_${editingVehicle?.id ?: "new"}_$index",
                                        width = 220,
                                        height = 160,
                                        crossfadeMillis = 140,
                                        modifier = Modifier.matchParentSize(),
                                        contentScale = ContentScale.Crop
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsCar,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(22.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            galleryImagesInput = galleryImagesInput.filterIndexed { i, _ -> i != index }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove gallery photo ${index + 1}",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Row 1: Title + Brand (matching website Edit Car layout)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { if (it.length <= 200) titleInput = it },
                        label = { Text("Title") },
                        placeholder = { Text("Leopard 5") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vehicle_title_input")
                    )
                    OutlinedTextField(
                        value = makeInput,
                        onValueChange = { if (it.length <= 80) makeInput = it },
                        label = { Text("Brand") },
                        placeholder = { Text("BYD") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vehicle_make_input")
                    )
                }

                // Row 2: Model + Year
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { if (it.length <= 120) modelInput = it },
                        label = { Text("Model") },
                        placeholder = { Text("SUV") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vehicle_model_input")
                    )
                    OutlinedTextField(
                        value = yearInput,
                        onValueChange = { if (it.length <= 4) yearInput = it.filter(Char::isDigit) },
                        label = { Text("Year") },
                        placeholder = { Text(currentYear.toString()) },
                        isError = attemptedSubmit && !yearValid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vehicle_year_input")
                    )
                }

                // Row 3: Mileage + Color
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = mileageInput,
                        onValueChange = { if (it.length <= 60) mileageInput = it },
                        label = { Text("Mileage") },
                        placeholder = { Text("1200") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vehicle_mileage_input")
                    )
                    OutlinedTextField(
                        value = colorInput,
                        onValueChange = { if (it.length <= 80) colorInput = it },
                        label = { Text("Color") },
                        placeholder = { Text("Black / Smoke Grey") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vehicle_color_input")
                    )
                }

                // Row 4: Fuel Type + Transmission
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = fuelTypeInput,
                        onValueChange = { if (it.length <= 80) fuelTypeInput = it },
                        label = { Text("Fuel Type") },
                        placeholder = { Text("Hybrid") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vehicle_fuel_type_input")
                    )
                    OutlinedTextField(
                        value = transmissionInput,
                        onValueChange = { if (it.length <= 80) transmissionInput = it },
                        label = { Text("Transmission") },
                        placeholder = { Text("Automatic") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vehicle_transmission_input")
                    )
                }

                // Quick-fill Fuel Type & Transmission chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Hybrid", "Electric", "Petrol", "Diesel").forEach { preset ->
                        SuggestionChip(
                            onClick = { fuelTypeInput = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    listOf("Automatic", "Manual").forEach { preset ->
                        SuggestionChip(
                            onClick = { transmissionInput = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Row 5: Price + Original Price
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
                        label = { Text("Price") },
                        prefix = { Text("ETB ", fontFamily = JetBrainsMonoFontFamily) },
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
                        prefix = { Text("ETB ", fontFamily = JetBrainsMonoFontFamily) },
                        isError = attemptedSubmit && !originalPriceValid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vehicle_original_price_input")
                    )
                }

                // Origin
                OutlinedTextField(
                    value = originInput,
                    onValueChange = { if (it.length <= 100) originInput = it },
                    label = { Text("Origin") },
                    placeholder = { Text("Dubai / China / Japan") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vehicle_origin_input")
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
                    label = { Text("Description") },
                    minLines = 3,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vehicle_description_input")
                )

                OutlinedTextField(
                    value = tiktokUrlInput,
                    onValueChange = { if (it.length <= 500) tiktokUrlInput = it },
                    label = { Text("TikTok Video URL") },
                    placeholder = { Text("https://www.tiktok.com/…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )

                if (attemptedSubmit && !formValid) {
                    Text(
                        text = "If entered, Year must be between 1950 and 2100 and Price must be valid.",
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
                        onClick = onBack,
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
                            if (formValid) {
                                val resolvedImageUrl = imageUrlInput.trim()
                                    .ifEmpty { galleryImagesInput.firstOrNull()?.trim().orEmpty() }
                                    .ifEmpty { "https://alifcarimport.com/placeholder.jpg" }
                                onSubmit(
                                    VehicleDraft(
                                        title = titleInput.trim(),
                                        make = makeInput.trim(),
                                        model = modelInput.trim(),
                                        year = resolvedYear,
                                        price = resolvedPrice,
                                        originalPrice = parsedOriginalPrice,
                                        mileage = mileageInput.trim(),
                                        transmission = transmissionInput.trim(),
                                        fuelType = fuelTypeInput.trim(),
                                        color = colorInput.trim(),
                                        origin = originInput.trim(),
                                        imageUrl = resolvedImageUrl,
                                        galleryImages = galleryImagesInput,
                                        description = descriptionInput.trim(),
                                        tiktokUrl = tiktokUrlInput.trim(),
                                        status = selectedStatus,
                                        featured = featured
                                    )
                                )
                            }
                        },
                        enabled = !isSaving && !isProcessingPhoto,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(52.dp)
                            .testTag("submit_vehicle_button")
                    ) {
                        if (isSaving || isProcessingPhoto) {
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
}
