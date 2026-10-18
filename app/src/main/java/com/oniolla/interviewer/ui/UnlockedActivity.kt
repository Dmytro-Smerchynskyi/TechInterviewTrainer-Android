package com.oniolla.interviewer.ui

import android.app.KeyguardManager
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun ComponentActivity.isResumedAndUnlocked(): Boolean =
    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && isUnlocked()

private fun ComponentActivity.isUnlocked(): Boolean =
    getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

/** Wait for the actual unlock state; USER_PRESENT may precede KeyguardManager's state update. */
internal fun ComponentActivity.runWhenResumedAndUnlocked(action: () -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (!isUnlocked()) delay(250)
            action()
        }
    }
}
