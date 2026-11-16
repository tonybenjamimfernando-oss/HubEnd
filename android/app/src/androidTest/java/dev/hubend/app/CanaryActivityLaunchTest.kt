package dev.hubend.app

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Canário do ambiente Android: esta Activity é uma android.app.Activity pura,
 * sem Capacitor BridgeActivity e sem WebView.
 */
@RunWith(AndroidJUnit4::class)
class CanaryActivityLaunchTest {
    @Test(timeout = 5_000)
    fun plainAndroidActivityReachesResumedWithinFiveSeconds() {
        ActivityScenario.launch(CanaryActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
