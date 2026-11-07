package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.models.SupportTicket
import com.example.data.models.SupportTicketMessage
import com.example.ui.theme.*
import com.example.utils.ImageUtils
import com.example.viewmodel.RiderViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportChatScreen(
    ticketId: String,
    viewModel: RiderViewModel,
    onSafeExit: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeTicket by viewModel.activeTicket.collectAsState()
    val messages by viewModel.activeTicketMessages.collectAsState()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var selectedPhotoBase64 by remember { mutableStateOf<String?>(null) }
    var previewEnlargedImage by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    LaunchedEffect(ticketId) {
        viewModel.selectSupportTicket(ticketId)
    }

    // Scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val base64 = ImageUtils.compressBitmapToBase64(bitmap, quality = 70)
            selectedPhotoBase64 = base64
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = ImageUtils.compressUriToBase64(context, uri)
            if (base64 != null) {
                selectedPhotoBase64 = base64
            }
        }
    }

    val ticket = activeTicket
    val status = ticket?.status?.lowercase() ?: "open"
    val isOpen = status == "open"
    val isAccepted = status == "accepted"
    val isResolved = status == "resolved"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = ticket?.title ?: "Support Chat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                        Text(
                            text = if (ticket != null) {
                                "${ticket.displayOrderNumber} • ${ticket.reasonLabel}"
                            } else "Connecting...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onSafeExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Safe Exit Button to return to main map
                    Button(
                        onClick = onSafeExit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("safe_exit_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Safe Exit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = Modifier.testTag("support_chat_screen")
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top Status Banner
            when {
                isOpen -> {
                    // Waiting banner
                    Surface(
                        color = Color(0xFFFFF8E1),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFF57F17)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Waiting for support to accept your report",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
                isAccepted -> {
                    // In progress banner
                    Surface(
                        color = Color(0xFFE8F5E9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA5D6A7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SupportAgent,
                                contentDescription = null,
                                tint = DelivioGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Report accepted • Support agent is assisting you",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF1B5E20)
                            )
                        }
                    }
                }
                isResolved -> {
                    // Resolved card with note
                    Surface(
                        color = Color(0xFFEDE7F6),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB39DDB)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF512DA8), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Report Resolved",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF512DA8)
                                )
                            }
                            if (!ticket?.resolutionNote.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Resolution Note: ${ticket?.resolutionNote}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF311B92)
                                )
                            }
                        }
                    }
                }
            }

            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // Ticket header card inside chat
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Booking Issue Summary",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Title: ${ticket?.title ?: ""}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Reason: ${ticket?.reasonLabel ?: ""}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (ticket?.splitItems?.isNotEmpty() == true) {
                                Text(
                                    text = "Split Items: ${ticket.splitItems.joinToString(", ")}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF7B1FA2),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                items(messages) { msg ->
                    ChatBubbleItem(
                        message = msg,
                        onImageClick = { previewEnlargedImage = it }
                    )
                }
            }

            // Attached photo preview before sending
            selectedPhotoBase64?.let { photoData ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = photoData,
                                contentDescription = "Attached",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Photo attached (< 300 KB)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { selectedPhotoBase64 = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = DelivioRed)
                        }
                    }
                }
            }

            // Message Input Box
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Camera & Gallery buttons (enabled only when accepted)
                    IconButton(
                        onClick = { cameraLauncher.launch(null) },
                        enabled = isAccepted && !isSending,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "Camera",
                            tint = if (isAccepted) DelivioGreen else MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = isAccepted && !isSending,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "Gallery",
                            tint = if (isAccepted) DelivioYellowDark else MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    // Text Field (Disabled while status is open or resolved)
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = when {
                                    isOpen -> "Waiting for support to accept report..."
                                    isResolved -> "Report is resolved"
                                    else -> "Type your message..."
                                },
                                fontSize = 13.sp
                            )
                        },
                        enabled = isAccepted && !isSending,
                        singleLine = false,
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )

                    // Send Button
                    val canSend = isAccepted && !isSending && (inputText.isNotBlank() || selectedPhotoBase64 != null)
                    IconButton(
                        onClick = {
                            if (canSend) {
                                isSending = true
                                val textToSend = inputText.trim()
                                val photoToSend = selectedPhotoBase64
                                inputText = ""
                                selectedPhotoBase64 = null

                                viewModel.sendTicketMessage(
                                    ticketId = ticketId,
                                    text = textToSend,
                                    imageData = photoToSend,
                                    onSuccess = { isSending = false },
                                    onError = { isSending = false }
                                )
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (canSend) DelivioYellow else MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("send_chat_message_button")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = TextOnYellow)
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (canSend) TextOnYellow else MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Enlarged Image Dialog
    previewEnlargedImage?.let { imgData ->
        Dialog(onDismissRequest = { previewEnlargedImage = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = imgData,
                    contentDescription = "Full Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { previewEnlargedImage = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(
    message: SupportTicketMessage,
    onImageClick: (String) -> Unit
) {
    val isRider = message.isRider
    val alignment = if (isRider) Alignment.End else Alignment.Start
    val bubbleColor = if (isRider) DelivioYellowLight.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Sender label
        Text(
            text = if (isRider) "You" else "Delivio Support",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isRider) DelivioYellowDark else DelivioGreen,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isRider) 16.dp else 2.dp,
                bottomEnd = if (isRider) 2.dp else 16.dp
            ),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // If message has image
                if (!message.imageData.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onImageClick(message.imageData) }
                    ) {
                        AsyncImage(
                            model = message.imageData,
                            contentDescription = "Attachment",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (message.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Timestamp
                message.createdAt?.let { ts ->
                    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                    val timeStr = sdf.format(ts.toDate())
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = timeStr,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}
