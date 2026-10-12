package com.oniolla.interviewer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val background: Color,
    val splashBackground1: Color,
    val splashBackground2: Color,
    val leftGradient: Color,
    val rightGradient: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val secondaryDisabledText: Color,
    val progressBackground: Color,
    val container: Color,
    val containerDisabled: Color,
    val stroke: Color,
    val strokeDisabled: Color,
    val link: Color,
    val accent: Color,
    val red: Color,
    val success: Color,
)

val LocalAppColors = staticCompositionLocalOf<AppColors> {
    error("No colors provided")
}

private val DarkAppColors = AppColors(
    background = BackgroundDark,
    splashBackground1 = SplashBackground1Dark,
    splashBackground2 = SplashBackground2Dark,
    leftGradient = LeftGradientDark,
    rightGradient = RightGradientDark,
    primaryText = PrimaryTextDark,
    secondaryText = SecondaryTextDark,
    secondaryDisabledText = SecondaryTextDisabledDark,
    progressBackground = ProgressBackgroundDark,
    container = ContainerDark,
    containerDisabled = ContainerDisabledDark,
    stroke = StrokeDark,
    strokeDisabled = StrokeDisabledDark,
    link = LinkDark,
    accent = AccentDark,
    red = RedDark,
    success = SuccessDark
)

private val LightAppColors = AppColors(
    background = BackgroundLight,
    splashBackground1 = SplashBackground1Light,
    splashBackground2 = SplashBackground2Light,
    leftGradient = LeftGradientLight,
    rightGradient = RightGradientLight,
    primaryText = PrimaryTextLight,
    secondaryText = SecondaryTextLight,
    secondaryDisabledText = SecondaryTextDisabledLight,
    progressBackground = ProgressBackgroundLight,
    container = ContainerLight,
    containerDisabled = ContainerDisabledLight,
    stroke = StrokeLight,
    strokeDisabled = StrokeDisabledLight,
    link = LinkLight,
    accent = AccentLight,
    red = RedLight,
    success = SuccessLight
)

private val DarkColorScheme = darkColorScheme(
    primary = SecondaryTextDark,
    secondary = SplashBackground1Dark,
    tertiary = SplashBackground2Dark,

    background = BackgroundDark,
)

private val LightColorScheme = lightColorScheme(
    primary = SecondaryTextLight,
    secondary = SplashBackground1Light,
    tertiary = SplashBackground2Light,

    background = BackgroundLight,

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun InterviewTrainerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(
        LocalAppColors provides appColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}