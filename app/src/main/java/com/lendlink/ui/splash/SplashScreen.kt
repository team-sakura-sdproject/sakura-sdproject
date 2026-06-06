package com.lendlink.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val skyBlue = Color(0xFF29B6F6)

    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.5f) }
    val dotAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Animate title in
        launch { alpha.animateTo(1f, tween(700, easing = EaseOutCubic)) }
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        delay(400)
        dotAlpha.animateTo(1f, tween(400))
        delay(1700)  // Hold — total ~3 seconds
        alpha.animateTo(0f, tween(400))
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(skyBlue),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha.value)
        ) {
            // Chain-link icon (simple shapes)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.scale(scale.value)
            ) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f))
                )
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.size(40.dp, 8.dp).background(Color.White))
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f))
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "LendLink",
                fontSize = 58.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp,
                modifier = Modifier.scale(scale.value)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Peer Lending Platform",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f),
                letterSpacing = 1.sp,
                modifier = Modifier.alpha(dotAlpha.value)
            )

            Spacer(Modifier.height(40.dp))
            AnimatedDots(modifier = Modifier.alpha(dotAlpha.value))
        }
    }
}

@Composable
private fun AnimatedDots(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "dots")
    val a1 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "d1")
    val a2 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500, delayMillis = 170), RepeatMode.Reverse), label = "d2")
    val a3 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500, delayMillis = 340), RepeatMode.Reverse), label = "d3")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (a in listOf(a1, a2, a3)) {
            Box(modifier = Modifier.size(9.dp).alpha(a)
                .clip(CircleShape).background(Color.White))
        }
    }
}
