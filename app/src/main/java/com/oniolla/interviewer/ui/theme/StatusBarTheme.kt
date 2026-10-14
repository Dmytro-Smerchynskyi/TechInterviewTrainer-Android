package com.oniolla.interviewer.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.oniolla.interviewer.data.ThemeMode

internal fun ComponentActivity.applyStatusBarTheme(theme: ThemeMode) {
    val statusBarStyle = when (theme) {
        ThemeMode.DARK -> SystemBarStyle.dark(Color.TRANSPARENT)
        ThemeMode.LIGHT -> SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        ThemeMode.SYSTEM -> SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
    }
    enableEdgeToEdge(statusBarStyle = statusBarStyle)
}

@Composable
internal fun StatusBarTheme(activity: ComponentActivity, theme: ThemeMode) {
    val systemDark = isSystemInDarkTheme()
    DisposableEffect(activity, theme, systemDark) {
        activity.applyStatusBarTheme(theme)
        onDispose { }
    }
}
