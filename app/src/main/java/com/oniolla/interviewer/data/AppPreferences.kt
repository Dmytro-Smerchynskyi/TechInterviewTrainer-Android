package com.oniolla.interviewer.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Singleton
class AppPreferences @Inject constructor(private val preferences: SharedPreferences) {
    private val _theme = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == preferences.getString(THEME, null) } ?: ThemeMode.SYSTEM
    )
    val theme = _theme.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        preferences.edit().putString(THEME, mode.name).apply()
    }

    fun clear() {
        check(preferences.edit().clear().commit()) { "Unable to clear preferences" }
        _theme.value = ThemeMode.SYSTEM
    }

    private companion object { const val THEME = "theme_mode" }
}
