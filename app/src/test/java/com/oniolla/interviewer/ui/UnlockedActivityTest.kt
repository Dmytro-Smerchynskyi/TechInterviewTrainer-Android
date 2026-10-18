package com.oniolla.interviewer.ui

import android.app.KeyguardManager
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.activity.ComponentActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UnlockedActivityTest {
    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity
    private lateinit var power: PowerManager
    private lateinit var keyguard: KeyguardManager
    private var starts = 0

    @Before fun setup() {
        controller = Robolectric.buildActivity(ComponentActivity::class.java)
        activity = controller.get()
        power = activity.getSystemService(PowerManager::class.java)
        keyguard = activity.getSystemService(KeyguardManager::class.java)
        shadowOf(power).setIsInteractive(false)
        shadowOf(keyguard).setKeyguardLocked(true)
        shadowOf(keyguard).setIsDeviceLocked(true)
        controller.create()
        activity.runWhenResumedAndUnlocked { starts++ }
        controller.start().resume()
    }

    @After fun cleanup() { controller.pause().stop().destroy() }

    private fun broadcast(action: String) {
        activity.sendBroadcast(Intent(action))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
    }

    @Test fun launchWithScreenOffWaitsForUnlockEvenAfterScreenTurnsOn() {
        assertEquals(0, starts)
        shadowOf(power).setIsInteractive(true)
        broadcast(Intent.ACTION_SCREEN_ON)
        assertEquals(0, starts)
        shadowOf(keyguard).setKeyguardLocked(false)
        shadowOf(keyguard).setIsDeviceLocked(false)
        broadcast(Intent.ACTION_USER_PRESENT)
        assertEquals(1, starts)
        broadcast(Intent.ACTION_USER_PRESENT)
        assertEquals(1, starts)
    }

    @Test fun unlockingAfterUserPresentWithoutAnotherLifecycleEventStillStarts() {
        shadowOf(power).setIsInteractive(true)
        // Some devices dispatch USER_PRESENT before KeyguardManager reports the unlock.
        broadcast(Intent.ACTION_USER_PRESENT)
        assertEquals(0, starts)
        shadowOf(keyguard).setKeyguardLocked(false)
        shadowOf(keyguard).setIsDeviceLocked(false)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        assertEquals(1, starts)
    }

    @Test fun unlockWhileActivityIsPausedWaitsForResume() {
        controller.pause()
        shadowOf(power).setIsInteractive(true)
        shadowOf(keyguard).setKeyguardLocked(false)
        shadowOf(keyguard).setIsDeviceLocked(false)
        broadcast(Intent.ACTION_USER_PRESENT)
        assertEquals(0, starts)
        controller.resume()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
        assertEquals(1, starts)
    }

    @Test fun eachResumeWaitsForUnlockAndStartsOnlyOnce() {
        shadowOf(power).setIsInteractive(true)
        shadowOf(keyguard).setKeyguardLocked(false)
        shadowOf(keyguard).setIsDeviceLocked(false)
        broadcast(Intent.ACTION_USER_PRESENT)
        assertEquals(1, starts)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        assertEquals(1, starts)

        controller.pause()
        shadowOf(power).setIsInteractive(false)
        shadowOf(keyguard).setKeyguardLocked(true)
        shadowOf(keyguard).setIsDeviceLocked(true)
        controller.resume()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        assertEquals(1, starts)
        shadowOf(power).setIsInteractive(true)
        shadowOf(keyguard).setKeyguardLocked(false)
        shadowOf(keyguard).setIsDeviceLocked(false)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
        assertEquals(2, starts)
    }
}
