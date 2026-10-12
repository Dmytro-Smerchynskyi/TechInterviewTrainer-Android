package com.oniolla.interviewer.ui.home

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import com.oniolla.interviewer.BuildConfig
import com.oniolla.interviewer.data.resolveTopicIconUrl
import com.oniolla.interviewer.R
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.oniolla.interviewer.data.model.TopicVisual
import com.oniolla.interviewer.ui.theme.AppTheme

@Composable
internal fun FigmaIcon(asset: String, modifier: Modifier = Modifier, tint: Color? = null) {
    Image(
        painter = painterResource(figmaIconResource(asset)),
        contentDescription = null,
        modifier = modifier.size(if (asset == "questions") 20.dp else 24.dp),
        colorFilter = tint?.let { ColorFilter.tint(it) }
    )
}

private fun figmaIconResource(asset: String): Int = when (asset) {
    "calendar" -> R.drawable.home_icon_calendar
    "chevron" -> R.drawable.home_icon_chevron
    "code" -> R.drawable.home_icon_code
    "favorite" -> R.drawable.home_icon_favorite
    "home" -> R.drawable.home_icon_home
    "practice" -> R.drawable.home_icon_practice
    "questions" -> R.drawable.home_icon_questions
    "search" -> R.drawable.home_icon_search
    "stats" -> R.drawable.home_icon_stats
    "topics" -> R.drawable.home_icon_topics
    "flow" -> R.drawable.home_icon_flow
    "rxjava" -> R.drawable.home_icon_rxjava
    else -> error("Unknown Figma icon: $asset")
}

internal fun parseTopicColor(value: String?, fallback: Color): Color {
    val hex = value?.removePrefix("#") ?: return fallback
    if (hex.length != 6 && hex.length != 8) return fallback
    val number = hex.toLongOrNull(16) ?: return fallback
    return Color(if (hex.length == 6) number or 0xFF000000L else number)
}

@Composable
internal fun topicTint(visual: TopicVisual?): Color =
    parseTopicColor(if (AppTheme.colors.isDark) visual?.darkColor else visual?.lightColor,
        AppTheme.colors.accent)

@Composable
internal fun TopicIcon(
    visual: TopicVisual?,
    name: String,
    modifier: Modifier = Modifier,
    badge: Boolean = true
) {
    val tint = topicTint(visual)
    val preview = LocalInspectionMode.current
    val iconUrl = if (preview && visual?.iconUrl?.startsWith("preview://figma/") == true)
        visual.iconUrl else resolveTopicIconUrl(visual?.iconUrl)
    var loading by remember(iconUrl, visual?.iconVersion) { mutableStateOf(iconUrl != null) }
    var failed by remember(iconUrl, visual?.iconVersion) { mutableStateOf(iconUrl == null) }
    val context = LocalContext.current
    val request = remember(iconUrl, visual?.iconVersion) {
        ImageRequest.Builder(context).data(iconUrl)
            .memoryCacheKey("$iconUrl#${visual?.iconVersion}")
            .diskCacheKey("$iconUrl#${visual?.iconVersion}")
            .crossfade(true).build()
    }
    Box(
        modifier = modifier.then(
            if (badge) Modifier.clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.13f))
            else Modifier
        ),
        contentAlignment = Alignment.Center
    ) {
        if (preview && iconUrl?.startsWith("preview://figma/") == true) {
            FigmaIcon(iconUrl.substringAfterLast('/').removeSuffix(".svg"), tint = tint)
        } else {
            if (iconUrl != null) {
                AsyncImage(
                    model = request,
                    contentDescription = name,
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(tint),
                    onLoading = { loading = true; failed = false },
                    onSuccess = { loading = false; failed = false },
                    onError = {
                        loading = false
                        failed = true
                        if (BuildConfig.DEBUG) Log.w("TopicIcon", "Unable to load topic icon", it.result.throwable)
                    }
                )
            }
            if (loading) ShimmerAnimation { ShimmerBlock(Modifier.fillMaxSize()) }
            if (failed) FigmaIcon("topics", Modifier.size(24.dp), tint)
        }
    }
}

internal fun percent(value: Double): Int =
    if (value.isFinite()) value.coerceIn(0.0, 100.0).toInt() else 0
