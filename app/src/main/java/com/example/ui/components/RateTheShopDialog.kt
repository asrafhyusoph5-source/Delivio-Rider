package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.Order
import com.example.ui.theme.*

@Composable
fun RateTheShopDialog(
    order: Order,
    onSubmit: (rating: Int, comment: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRating by remember { mutableStateOf(5) }
    var commentText by remember { mutableStateOf("") }

    val shopName = order.displayMerchantName.ifBlank { "Partner Merchant" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .testTag("rate_the_shop_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Shop icon badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(DelivioYellow.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Shop",
                        tint = DelivioYellowDark,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Rate the shop",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = shopName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DelivioYellowDark,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "How was your pickup experience? Your review is anonymous and helps improve merchant prep times.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Interactive 5-star selector (min touch target size handled by padding)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..5).forEach { star ->
                        val isFilled = star <= selectedRating
                        IconButton(
                            onClick = { selectedRating = star },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("rate_shop_star_$star")
                        ) {
                            Icon(
                                imageVector = if (isFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$star Stars",
                                tint = if (isFilled) DelivioYellowDark else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Optional comment up to 200 characters
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { if (it.length <= 200) commentText = it },
                    label = { Text("Comment (optional)") },
                    placeholder = { Text("e.g. Order was ready promptly, courteous staff") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rate_shop_comment_input"),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3,
                    supportingText = {
                        Text(
                            text = "${commentText.length}/200",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Buttons: Skip & Submit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("rate_shop_skip_button")
                    ) {
                        Text("Skip", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onSubmit(selectedRating, commentText)
                        },
                        enabled = selectedRating in 1..5,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DelivioYellow,
                            contentColor = TextOnYellow
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .testTag("rate_shop_submit_button")
                    ) {
                        Text("Submit Rating", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
