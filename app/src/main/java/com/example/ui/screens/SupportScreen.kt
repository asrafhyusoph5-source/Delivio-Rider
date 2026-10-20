package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ChatBubble
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit
) {
    val supportMessages by viewModel.supportChatMessages.collectAsState()
    var activeTab by remember { mutableIntStateOf(0) }
    var chatInput by remember { mutableStateOf("") }
    var faqSearch by remember { mutableStateOf("") }

    val faqs = listOf(
        Pair("How are surge fares computed in Palawan?", "Surge bonuses (+₱20 to +₱50) activate automatically when incoming customer orders exceed active rider supply in designated hot zones such as Rizal Ave and San Pedro."),
        Pair("When are earnings deposited to GCash?", "You can initiate instant payouts at any time through the Wallet tab. Instant cash-out delivers funds within 60 seconds."),
        Pair("What if the customer is unreachable?", "Wait at the customer drop-off pin. Tap 'Report Delivery Issue' > 'Customer not answering'. A 5-minute countdown starts, after which you can cancel without affecting completion rate."),
        Pair("How do I update my motorcycle plate number?", "Open My Profile > Edit (top-right pencil icon) and enter your updated plate number or submit newly registered OR/CR.")
    )

    val activeCall by viewModel.activeCall.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rider Support", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openDialer("+63 48 433 0000", "Delivio 24/7 Rider Support") }) {
                        Icon(Icons.Default.Phone, contentDescription = "Call Support", tint = DelivioGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = Modifier.testTag("support_screen")
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(selectedTabIndex = activeTab, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Live Support Chat", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("FAQ & Help Center", fontWeight = FontWeight.Bold) }
                )
            }

            if (activeTab == 0) {
                // Interactive Support Chat
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(supportMessages) { msg ->
                            ChatBubble(message = msg)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("Ask support anything...", fontSize = 14.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                if (chatInput.isNotBlank()) {
                                    viewModel.sendSupportMessage(chatInput.trim())
                                    chatInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(DelivioYellow)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = TextOnYellow)
                        }
                    }
                }
            } else {
                // FAQ Section
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = faqSearch,
                            onValueChange = { faqSearch = it },
                            placeholder = { Text("Search help topics...", fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, "Search") },
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    items(faqs.filter { it.first.contains(faqSearch, ignoreCase = true) || it.second.contains(faqSearch, ignoreCase = true) }) { (q, a) ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(q, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(a, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // Safe in-app call overlay
    activeCall?.let { callInfo ->
        com.example.ui.components.InAppCallDialog(
            callInfo = callInfo,
            onEndCall = { viewModel.endActiveCall() },
            onOpenSystemDialer = { phone -> viewModel.launchExternalDialer(phone) }
        )
    }
}
