package me.rerere.rikkahub.overlay.pet

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.rerere.rikkahub.data.companion.model.CompanionEmotionState
import me.rerere.rikkahub.data.model.CompanionPixelPetSkin

private const val PET_ASSET_URL = "file:///android_asset/pet/index.html"

/**
 * Pixel desktop pet: transparent WebView SVG character + Compose speech bubble.
 */
class PixelPetRenderer : PetRenderer {
    @Composable
    override fun Content(
        state: CompanionPetState,
        onClick: () -> Unit,
        besidePet: @Composable () -> Unit,
    ) {
        val speaking = state.bubbleText.isNotBlank()
        val bubbleBg = Color.Black.copy(alpha = 0.58f)
        val onBubble = Color.White.copy(alpha = 0.95f)
        val onClickState = rememberUpdatedState(onClick)
        var webView by remember { mutableStateOf<WebView?>(null) }

        LaunchedEffect(state.emotion, state.pixelPetSkin, speaking, webView) {
            val view = webView ?: return@LaunchedEffect
            pushPetState(view, state.pixelPetSkin, state.emotion, speaking)
        }

        DisposableEffect(Unit) {
            onDispose {
                webView?.apply {
                    stopLoading()
                    destroy()
                }
                webView = null
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(4.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AndroidView(
                    factory = { context ->
                        createPetWebView(context) {
                            onClickState.value.invoke()
                        }.also { webView = it }
                    },
                    update = { view ->
                        pushPetState(view, state.pixelPetSkin, state.emotion, speaking)
                    },
                    modifier = Modifier.size(72.dp),
                )
            }

            besidePet()

            AnimatedVisibility(
                visible = speaking,
                enter = fadeIn(tween(180)) +
                    slideInHorizontally(tween(220)) { it / 3 } +
                    scaleIn(initialScale = 0.88f, animationSpec = tween(220)),
                exit = fadeOut(tween(140)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 2.dp),
                ) {
                    Canvas(modifier = Modifier.size(width = 8.dp, height = 12.dp)) {
                        val path = Path().apply {
                            moveTo(size.width, 0f)
                            lineTo(0f, size.height / 2f)
                            lineTo(size.width, size.height)
                            close()
                        }
                        drawPath(path, color = bubbleBg)
                    }
                    Box(
                        modifier = Modifier
                            .widthIn(min = 56.dp, max = 148.dp)
                            .background(bubbleBg, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = state.bubbleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = onBubble,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createPetWebView(
    context: android.content.Context,
    onPetClick: () -> Unit,
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = WebView.OVER_SCROLL_NEVER
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = true
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
        }
        addJavascriptInterface(PetJsBridge(onPetClick), "PetBridge")
        webViewClient = WebViewClient()
        loadUrl(PET_ASSET_URL)
    }
}

private class PetJsBridge(
    private val onPetClick: () -> Unit,
) {
    @JavascriptInterface
    fun onPetClick() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onPetClick.invoke()
        }
    }
}

private fun pushPetState(
    webView: WebView,
    skin: CompanionPixelPetSkin,
    emotion: CompanionEmotionState,
    speaking: Boolean,
) {
    val skinJs = skin.assetId
    val emotionJs = emotion.name
    val speakingJs = if (speaking) "true" else "false"
    webView.post {
        webView.evaluateJavascript(
            "window.setSkin && window.setSkin('$skinJs');" +
                "window.setEmotion && window.setEmotion('$emotionJs');" +
                "window.setSpeaking && window.setSpeaking($speakingJs);",
            null,
        )
    }
}
