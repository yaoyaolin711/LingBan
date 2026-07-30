package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.ui.theme.SolaceTheme

@Composable
fun AssistantBackground(setting: Settings, modifier: Modifier) {
    val assistant = setting.getCurrentAssistant()
    if (assistant.useGradientBackground) {
        MeshGradientBackground(modifier = modifier)
        return
    }
    if (assistant.background != null) {
        val colors = SolaceTheme.colorScheme
        val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
        val context = LocalContext.current
        val density = LocalDensity.current
        // Decode near screen width — avoids full-res bitmap on low-end devices
        val targetPx = with(density) { 1080.dp.roundToPx() }
        val imageRequest = remember(assistant.background, targetPx) {
            ImageRequest.Builder(context)
                .data(assistant.background)
                .size(targetPx)
                .build()
        }
        Box(modifier = modifier) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(backgroundOpacity)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.background.copy(alpha = 0.25f),
                                colors.background.copy(alpha = 0.6f),
                            )
                        )
                    )
            )
        }
        return
    }

    SolaceAmbientBackground(modifier = modifier)
}

/**
 * Soft immersive chat wash — rose → pearl → champagne.
 */
@Composable
fun SolaceAmbientBackground(modifier: Modifier = Modifier) {
    val colors = SolaceTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to colors.lightRose.copy(alpha = 0.42f),
                        0.28f to colors.champagne.copy(alpha = 0.55f),
                        0.62f to colors.background,
                        1.0f to colors.champagne.copy(alpha = 0.35f),
                    )
                )
            )
    )
}
