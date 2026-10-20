package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DelivioYellow
import com.example.ui.theme.TextOnYellow
import kotlin.math.roundToInt

@Composable
fun SlideToConfirmButton(
    text: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val thumbSize = 52.dp
    val thumbSizePx = with(density) { thumbSize.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(4.dp, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .background(if (enabled) DelivioYellow else Color.Gray.copy(alpha = 0.5f))
            .testTag("slide_to_confirm_button"),
        contentAlignment = Alignment.CenterStart
    ) {
        val maxOffsetPx = constraints.maxWidth.toFloat() - thumbSizePx - with(density) { 8.dp.toPx() }

        // Center Instruction Text
        Text(
            text = text,
            color = TextOnYellow,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 60.dp)
        )

        // Draggable Thumb Slider
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt() + with(density) { 4.dp.roundToPx() }, 0) }
                .size(thumbSize)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A))
                .draggable(
                    enabled = enabled,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetX = (offsetX + delta).coerceIn(0f, maxOffsetPx)
                    },
                    onDragStopped = {
                        if (offsetX >= maxOffsetPx * 0.75f) {
                            offsetX = 0f
                            onConfirmed()
                        } else {
                            offsetX = 0f
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Slide to confirm",
                tint = DelivioYellow,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
