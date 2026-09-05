package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun BeforeAfterSlider(
    beforeBitmap: Bitmap?,
    afterBitmap: Bitmap?,
    splitPosition: Float,
    onSplitPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSplitPosition by rememberUpdatedState(splitPosition)
    val onPositionChanged by rememberUpdatedState(onSplitPositionChange)

    if (beforeBitmap == null && afterBitmap == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Pilih foto untuk memulai pengolahan",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(afterBitmap != null) {
                if (afterBitmap != null) {
                    detectTapGestures { offset ->
                        if (size.width > 0) {
                            val newPos = (offset.x / size.width.toFloat()).coerceIn(0.01f, 0.99f)
                            onPositionChanged(newPos)
                        }
                    }
                }
            }
            .pointerInput(afterBitmap != null) {
                if (afterBitmap != null) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        if (size.width > 0) {
                            val totalWidth = size.width.toFloat()
                            val currentPx = currentSplitPosition * totalWidth
                            val newPx = currentPx + dragAmount
                            val newPos = (newPx / totalWidth).coerceIn(0.01f, 0.99f)
                            onPositionChanged(newPos)
                        }
                    }
                }
            }
            .testTag("before_after_container")
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()

        if (afterBitmap == null && beforeBitmap != null) {
            // Only original image loaded
            Image(
                bitmap = beforeBitmap.asImageBitmap(),
                contentDescription = "Original Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("single_source_image")
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Foto Asli (${beforeBitmap.width}x${beforeBitmap.height})",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else if (beforeBitmap != null && afterBitmap != null) {
            // Split Before & After view
            val splitPx = (currentSplitPosition * totalWidthPx).coerceIn(0f, totalWidthPx)

            // Enhanced Image (Right/Background)
            Image(
                bitmap = afterBitmap.asImageBitmap(),
                contentDescription = "Enhanced Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("enhanced_image")
            )

            // Original Image clipped to the left side
            Image(
                bitmap = beforeBitmap.asImageBitmap(),
                contentDescription = "Original Photo Clipped",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(
                            left = 0f,
                            top = 0f,
                            right = splitPx,
                            bottom = size.height
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    .testTag("original_image_clipped")
            )

            // Divider Line
            Box(
                modifier = Modifier
                    .offset { IntOffset(splitPx.roundToInt() - 1.dp.roundToPx(), 0) }
                    .width(2.5.dp)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary)
            )

            // Draggable Handle Indicator (Visual cue, touches handled at container level)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = splitPx.roundToInt() - 20.dp.roundToPx(),
                            y = (constraints.maxHeight / 2) - 20.dp.roundToPx()
                        )
                    }
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag("slider_handle"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CompareArrows,
                    contentDescription = "Geser perbandingan",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Labels: SEBELUM (Left) and SESUDAH (Right)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "SEBELUM",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Text(
                    text = "SESUDAH (AI)",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}
