package com.oniolla.interviewer.data

import android.content.Context
import coil3.SingletonImageLoader
import com.oniolla.interviewer.data.local.TopicDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalUserData @Inject constructor(
    private val database: TopicDatabase,
    private val statsCache: SessionStatsCache,
    private val preferences: AppPreferences,
    @ApplicationContext private val context: Context
) {
    suspend fun clear() = withContext(Dispatchers.IO) {
        statsCache.clear()
        database.clearAllTables()
        preferences.clear()
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
        try {
            androidx.credentials.CredentialManager.create(context).clearCredentialState(
                androidx.credentials.ClearCredentialStateRequest())
        } catch (_: androidx.credentials.exceptions.ClearCredentialException) {
            // Local sign-out still succeeds when the provider is unavailable.
        }
    }
}
