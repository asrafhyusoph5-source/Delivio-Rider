package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.AcceptOrderCheckItem
import com.example.data.models.AcceptOrderErrorDetails
import com.example.data.models.AcceptOrderRules
import com.example.ui.theme.DelivioGreenDark
import com.example.ui.theme.DelivioRed

@Composable
fun AcceptOrderErrorDialog(
    errorDetails: AcceptOrderErrorDetails,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showDetails by remember(errorDetails.orderId, errorDetails.exceptionMessage) {
        mutableStateOf(false)
    }
    val scrollState = rememberScrollState()

    val resolvedShortMessage = remember(errorDetails) {
        errorDetails.shortMessage.ifBlank {
            AcceptOrderRules.buildShortFailureMessage(
                checks = errorDetails.checks,
                fallbackMessage = errorDetails.exceptionMessage
            )
        }
    }

    fun formatFieldValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> {
                if (value.length > 50) {
                    "${value.take(25)}... (omitted ${value.length - 25} chars)"
                } else {
                    "\"$value\""
                }
            }
            is Map<*, *> -> {
                val inner = value.entries.joinToString(", ") { (k, v) ->
                    val vStr = v?.toString() ?: "null"
                    val truncated = if (vStr.length > 35) "${vStr.take(18)}..." else vStr
                    "$k: $truncated"
                }
                "{ $inner }"
            }
            is Iterable<*> -> {
                val inner = value.joinToString(", ") { item ->
                    val iStr = item?.toString() ?: "null"
                    if (iStr.length > 35) "${iStr.take(18)}..." else iStr
                }
                "[ $inner ]"
            }
            else -> {
                val str = value.toString()
                if (str.length > 60) "${str.take(30)}..." else str
            }
        }
    }

    val clipboardText = buildString {
        appendLine("=== ACCEPT ORDER ERROR ===")
        appendLine("Summary: $resolvedShortMessage")
        appendLine("Exception Message: ${errorDetails.exceptionMessage}")
        appendLine("Order ID: ${errorDetails.orderId}")
        appendLine("Order Current Status: ${errorDetails.currentStatus}")
        appendLine("FirebaseAuth UID: ${errorDetails.authUid}")
        if (errorDetails.checks.isNotEmpty()) {
            appendLine("Checks:")
            errorDetails.checks.forEach { chk ->
                val badge = if (chk.passed) "PASS" else "FAIL"
                appendLine("  [$badge] ${chk.label}: ${chk.detail}")
            }
        }
        appendLine("Fields Written Map:")
        if (errorDetails.fieldsWritten.isEmpty()) {
            appendLine("  (none)")
        } else {
            errorDetails.fieldsWritten.forEach { (k, v) ->
                appendLine("  • $k: ${formatFieldValue(v)}")
            }
        }
    }

    if (!showDetails) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Accept Order Failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Text(
                    text = "Accept Order Failed",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = resolvedShortMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("accept_error_short_message")
                )
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("dismiss_error_dialog_button")
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDetails = true },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("accept_error_details_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Details", fontWeight = FontWeight.SemiBold)
                }
            },
            modifier = Modifier.testTag("accept_order_error_dialog")
        )
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.85f)
                    .testTag("accept_order_error_details_view")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Accept Order Details",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Order ${errorDetails.orderId.ifBlank { "N/A" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (errorDetails.checks.isNotEmpty()) {
                            val sortedChecks = remember(errorDetails.checks) {
                                errorDetails.checks.sortedBy { it.passed }
                            }
                            Text(
                                text = "ACCEPT REQUIREMENTS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            sortedChecks.forEach { check ->
                                AcceptCheckRow(check = check)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "EXCEPTION MESSAGE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = errorDetails.exceptionMessage.ifEmpty { "Transaction was aborted or timed out." },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        DetailRow(
                            label = "CURRENT ORDER STATUS",
                            value = errorDetails.currentStatus.ifEmpty { "(missing)" },
                            isMono = false
                        )

                        DetailRow(
                            label = "CURRENT FIREBASEAUTH UID",
                            value = errorDetails.authUid.ifEmpty { "(not logged in)" },
                            isMono = true
                        )

                        if (errorDetails.fieldsWritten.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "FIELDS ATTEMPTED TO WRITE (${errorDetails.fieldsWritten.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    errorDetails.fieldsWritten.forEach { (fieldName, fieldValue) ->
                                        Text(
                                            text = "• $fieldName: ${formatFieldValue(fieldValue)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dismiss_error_dialog_button")
                        ) {
                            Text("Dismiss", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(clipboardText))
                                Toast.makeText(context, "Error details copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("copy_error_details_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy Details", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AcceptCheckRow(check: AcceptOrderCheckItem) {
    val bgColor = if (check.passed) {
        DelivioGreenDark.copy(alpha = 0.08f)
    } else {
        DelivioRed.copy(alpha = 0.10f)
    }
    val borderColor = if (check.passed) {
        DelivioGreenDark.copy(alpha = 0.30f)
    } else {
        DelivioRed.copy(alpha = 0.50f)
    }
    val statusColor = if (check.passed) DelivioGreenDark else DelivioRed
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("accept_check_${check.key}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (check.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = if (check.passed) "Passed" else "Failed",
                tint = statusColor,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = check.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (check.passed) "PASS" else "FAIL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor
                    )
                }
                if (check.detail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = check.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isMono: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp, 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
