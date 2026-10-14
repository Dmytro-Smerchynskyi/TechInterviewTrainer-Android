package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.AppPreferences
import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.SessionStatsCache
import com.oniolla.interviewer.data.SessionTokenStorage
import com.oniolla.interviewer.data.ThemeMode
import com.oniolla.interviewer.data.model.Specialization
import javax.inject.Inject

class ManageProfileUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val statsCache: SessionStatsCache,
    private val tokens: SessionTokenStorage,
    private val preferences: AppPreferences
) {
    val theme get() = preferences.theme
    fun cachedStats() = statsCache.observe(tokens.snapshot().version)
    suspend fun profile() = loadContent { authRepository.getSessionProfile() }
    suspend fun usage() = loadContent { authRepository.accountUsage() }
    suspend fun specialization(value: Specialization) = loadContent {
        authRepository.selectSpecialization(value).also {
            check(it.specialization == value.code) { "Specialization was not saved" }
        }
    }
    suspend fun language(value: String) = loadContent {
        authRepository.selectPreparationLanguage(value).also {
            check(it.preparationLanguage == value) { "Preparation language was not saved" }
        }
    }
    fun setTheme(value: ThemeMode) = preferences.setTheme(value)
    suspend fun signOut() = loadContent { authRepository.clearUserData() }
}
