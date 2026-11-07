package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.HelpCenterConstants
import com.example.data.models.HelpCenterReason
import com.example.data.models.Order
import com.example.ui.theme.*
import com.example.utils.ImageUtils
import com.example.viewmodel.RiderViewModel

enum class HelpCenterStep {
    SELECT_ORDER,
    SELECT_REASON,
    CHECKLIST_SPLIT_ITEMS,
    DETAILS_FORM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpCenterFlowScreen(
    viewModel: RiderViewModel,
    preselectedOrderId: String? = null,
    onBack: () -> Unit,
    onTicketCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val activeOrders by viewModel.activeOrders.collectAsState()
    val unresolvedTickets by viewModel.unresolvedTickets.collectAsState()

    // Filter candidate orders in batch that are at merchant or active
    val candidateOrders = remember(activeOrders) {
        activeOrders.ifEmpty { emptyList() }
    }

    var selectedOrder by remember {
        mutableStateOf(
            candidateOrders.firstOrNull { it.id == preselectedOrderId }
                ?: candidateOrders.firstOrNull()
        )
    }

    var selectedReason by remember { mutableStateOf<HelpCenterReason?>(null) }
    val splitItemsMap = remember { mutableStateMapOf<String, Boolean>() }

    var titleInput by remember { mutableStateOf("") }
    var detailsInput by remember { mutableStateOf("") }
    val attachedPhotos = remember { mutableStateListOf<String>() }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Navigation through the wizard steps
    var currentStep by remember {
        mutableStateOf(
            if (candidateOrders.size > 1 && preselectedOrderId == null) HelpCenterStep.SELECT_ORDER
            else HelpCenterStep.SELECT_REASON
        )
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null && attachedPhotos.size < 3) {
            val base64 = ImageUtils.compressBitmapToBase64(bitmap, quality = 70)
            attachedPhotos.add(base64)
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && attachedPhotos.size < 3) {
            val base64 = ImageUtils.compressUriToBase64(context, uri)
            if (base64 != null) {
                attachedPhotos.add(base64)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Problem with the booking",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (currentStep) {
                                HelpCenterStep.SELECT_ORDER -> "Step 1 of 3: Choose Order"
                                HelpCenterStep.SELECT_REASON -> "Step 1: Select Reason"
                                HelpCenterStep.CHECKLIST_SPLIT_ITEMS -> "Step 2: Items to Split"
                                HelpCenterStep.DETAILS_FORM -> "Step 3: Tell Us Your Problem"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (currentStep) {
                                HelpCenterStep.SELECT_ORDER -> onBack()
                                HelpCenterStep.SELECT_REASON -> {
                                    if (candidateOrders.size > 1) currentStep = HelpCenterStep.SELECT_ORDER
                                    else onBack()
                                }
                                HelpCenterStep.CHECKLIST_SPLIT_ITEMS -> currentStep = HelpCenterStep.SELECT_REASON
                                HelpCenterStep.DETAILS_FORM -> {
                                    if (selectedReason?.requiresSplitChecklist == true) {
                                        currentStep = HelpCenterStep.CHECKLIST_SPLIT_ITEMS
                                    } else {
                                        currentStep = HelpCenterStep.SELECT_REASON
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = Modifier.testTag("help_center_flow_screen")
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Check unresolved limit (At most 3 unresolved reports)
            if (unresolvedTickets.size >= 3) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(DelivioRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = DelivioRed, modifier = Modifier.size(32.dp))
                        }
                        Text(
                            text = "Unresolved Reports Limit Reached",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "You currently have 3 unresolved reports open. To ensure high quality resolution, at most 3 reports can be pending at a time. Please wait for our support team to respond.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Return to Map", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                return@Scaffold
            }

            when (currentStep) {
                // STEP 1: SELECT ORDER (if multiple orders in batch)
                HelpCenterStep.SELECT_ORDER -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Which order has an issue?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Select the affected order from your current trip batch:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(candidateOrders) { ord ->
                                val isSelected = selectedOrder?.id == ord.id
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) DelivioYellowLight.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.5.dp,
                                        if (isSelected) DelivioYellowDark else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedOrder = ord
                                            currentStep = HelpCenterStep.SELECT_REASON
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Order ${ord.displayOrderNumber}",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 15.sp
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "Merchant: ${ord.merchantName}",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${ord.items.size} item(s) • ${ord.customerArea}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = DelivioGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // STEP 2: SELECT REASON (10 predefined reasons)
                HelpCenterStep.SELECT_REASON -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        selectedOrder?.let { ord ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = DelivioGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Selected Order ${ord.displayOrderNumber} (${ord.merchantName})",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Text(
                            text = "What is the reason for reporting?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(HelpCenterConstants.REASONS) { reason ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedReason = reason
                                            if (reason.requiresSplitChecklist) {
                                                // Initialize items for split
                                                selectedOrder?.items?.forEach { itm ->
                                                    splitItemsMap[itm.name] = true
                                                }
                                                currentStep = HelpCenterStep.CHECKLIST_SPLIT_ITEMS
                                            } else {
                                                currentStep = HelpCenterStep.DETAILS_FORM
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = reason.label,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Icon(
                                            Icons.Default.ArrowForwardIos,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // STEP 2b: CHECKLIST WHICH ITEMS CAN'T BE CARRIED
                HelpCenterStep.CHECKLIST_SPLIT_ITEMS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF3E5F5),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AltRoute, contentDescription = null, tint = Color(0xFF7B1FA2))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Split Delivery Candidate",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF7B1FA2)
                                    )
                                    Text(
                                        text = "Delivio will dispatch another driver to pick up the items you can't carry.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF4A148C)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Which items can't you carry?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Select all items that are too large, heavy, or can't fit in your bag:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val itemsList = selectedOrder?.items ?: emptyList()

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(itemsList) { item ->
                                val isChecked = splitItemsMap[item.name] ?: false
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChecked) Color(0xFFEDE7F6) else MaterialTheme.colorScheme.surface
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isChecked) Color(0xFF7B1FA2) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            splitItemsMap[item.name] = !isChecked
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { splitItemsMap[item.name] = it },
                                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF7B1FA2))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "${item.name} x${item.quantity}",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            if (item.selections.isNotEmpty()) {
                                                item.selections.filter { it.isNotBlank() && it != "()" }.forEach { sel ->
                                                    Text(
                                                        text = "  └ $sel",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val selectedCount = splitItemsMap.count { it.value }
                                if (selectedCount == 0) {
                                    errorMessage = "Please select at least one item that cannot be carried."
                                } else {
                                    errorMessage = null
                                    currentStep = HelpCenterStep.DETAILS_FORM
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("Continue to Details", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }

                // STEP 3: "TELL US YOUR PROBLEM" FORM
                HelpCenterStep.DETAILS_FORM -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Summary card of order and reason
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Reporting Order ${selectedOrder?.displayOrderNumber ?: ""}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Reason: ${selectedReason?.label ?: ""}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (selectedReason?.requiresSplitChecklist == true) {
                                    val splitNames = splitItemsMap.filter { it.value }.keys.joinToString(", ")
                                    Text(
                                        text = "Split Items: $splitNames",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF7B1FA2)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Tell us your problem",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        // Title (Required, 3 to 80 chars, becomes ticket title)
                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = {
                                if (it.length <= 80) titleInput = it
                            },
                            label = { Text("Title *") },
                            placeholder = { Text("Brief summary (e.g. Bag is full, box too large)") },
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Required (3-80 chars)")
                                    Text("${titleInput.length}/80")
                                }
                            },
                            isError = titleInput.isNotEmpty() && (titleInput.length < 3 || titleInput.length > 80),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ticket_title_input")
                        )

                        // Details (Optional, max 500 chars)
                        OutlinedTextField(
                            value = detailsInput,
                            onValueChange = {
                                if (it.length <= 500) detailsInput = it
                            },
                            label = { Text("Details (Optional)") },
                            placeholder = { Text("Describe the situation with merchant or booking...") },
                            supportingText = {
                                Text("${detailsInput.length}/500")
                            },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ticket_details_input")
                        )

                        // Add Photos (up to 3, real camera or gallery pictures)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Add Photos (up to 3)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Real photos of the items, receipt, or vehicle (< 300 KB each):",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Photo thumbnails row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                attachedPhotos.forEachIndexed { index, photoData ->
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                    ) {
                                        AsyncImage(
                                            model = photoData,
                                            contentDescription = "Photo ${index + 1}",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        IconButton(
                                            onClick = { attachedPhotos.removeAt(index) },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .align(Alignment.TopEnd)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                if (attachedPhotos.size < 3) {
                                    // Camera button
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clickable { cameraLauncher.launch(null) }
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", tint = DelivioGreen)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Camera", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    // Gallery button
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clickable { galleryLauncher.launch("image/*") }
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Default.Image, contentDescription = "Gallery", tint = DelivioYellowDark)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Gallery", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        errorMessage?.let { err ->
                            Text(
                                text = err,
                                color = DelivioRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Submit Button
                        Button(
                            onClick = {
                                val trimmedTitle = titleInput.trim()
                                if (trimmedTitle.length !in 3..80) {
                                    errorMessage = "Please enter a title between 3 and 80 characters."
                                    return@Button
                                }
                                val order = selectedOrder
                                if (order == null) {
                                    errorMessage = "No order selected."
                                    return@Button
                                }
                                val reason = selectedReason
                                if (reason == null) {
                                    errorMessage = "No reason selected."
                                    return@Button
                                }

                                val splitItems = if (reason.requiresSplitChecklist) {
                                    splitItemsMap.filter { it.value }.keys.toList()
                                } else emptyList()

                                isSubmitting = true
                                errorMessage = null

                                viewModel.createSupportTicket(
                                    order = order,
                                    reasonKey = reason.key,
                                    reasonLabel = reason.label,
                                    splitItems = splitItems,
                                    title = trimmedTitle,
                                    details = detailsInput.trim(),
                                    photosBase64 = attachedPhotos.toList(),
                                    onSuccess = { ticketId ->
                                        isSubmitting = false
                                        onTicketCreated(ticketId)
                                    },
                                    onError = { err ->
                                        isSubmitting = false
                                        errorMessage = err
                                    }
                                )
                            },
                            enabled = !isSubmitting && titleInput.trim().length in 3..80,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DelivioYellow,
                                contentColor = TextOnYellow
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("submit_report_button")
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = TextOnYellow)
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Submit Report to Support", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
