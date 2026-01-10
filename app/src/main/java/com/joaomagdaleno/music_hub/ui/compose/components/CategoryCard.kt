package com.joaomagdaleno.music_hub.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaomagdaleno.music_hub.common.models.Shelf

@Composable
fun CategoryCard(
    category: Shelf.Category,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = runCatching {
        Color(android.graphics.Color.parseColor(category.backgroundColor ?: "#1DB954"))
    }.getOrDefault(MaterialTheme.colorScheme.primary)

    Box(
        modifier = modifier
            .width(160.dp)
            .height(90.dp) // Slightly shorter for better aspect ratio
            .clip(RoundedCornerShape(12.dp)) // More rounded
            .background(bgColor)
            .clickable { onClick() }
            .padding(12.dp)
            .graphicsLayer {
                clip = true
            }
    ) {
        // Decorative ghost text
        Text(
            text = category.title,
            color = Color.White.copy(alpha = 0.15f),
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 20.dp, y = 10.dp)
                .graphicsLayer {
                    rotationZ = -20f
                }
        )

        Text(
            text = category.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}
