package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LotteryBall(
    number: Int,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    isBonus: Boolean = false,
    isSelected: Boolean = false,
    isMatched: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val ballColors = when {
        isBonus -> listOf(Color(0xFFFFB300), Color(0xFFFF6F00))
        isMatched -> listOf(Color(0xFF00E676), Color(0xFF00A844))
        number in 1..10 -> listOf(Color(0xFFFF5252), Color(0xFFD32F2F))
        number in 11..20 -> listOf(Color(0xFF42A5F5), Color(0xFF1976D2))
        number in 21..30 -> listOf(Color(0xFFFFCA28), Color(0xFFF57C00))
        number in 31..40 -> listOf(Color(0xFF66BB6A), Color(0xFF388E3C))
        else -> listOf(Color(0xFFAB47BC), Color(0xFF7B1FA2))
    }

    val finalBackground = if (isSelected) {
        listOf(Color(0xFFD32F2F), Color(0xFF8E0000))
    } else {
        ballColors
    }

    val borderWidth = when {
        isBonus -> 2.5.dp
        isSelected -> 2.dp
        isMatched -> 2.5.dp
        else -> 0.dp
    }

    val borderColor = when {
        isBonus -> Color(0xFFFFE082)
        isSelected -> MaterialTheme.colorScheme.primary
        isMatched -> Color(0xFFB9F6CA)
        else -> Color.Transparent
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else Modifier

    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = if (isBonus || isMatched) 4.dp else 2.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        finalBackground[0].copy(alpha = 0.95f),
                        finalBackground[1]
                    )
                )
            )
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(borderWidth, borderColor, CircleShape)
                } else Modifier
            )
            .then(clickableModifier)
            .testTag("lottery_ball_$number"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = String.format("%02d", number),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42).sp
        )
    }
}
