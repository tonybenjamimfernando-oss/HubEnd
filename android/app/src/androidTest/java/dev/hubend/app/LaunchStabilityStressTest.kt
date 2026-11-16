package dev.hubend.app

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchStabilityStressTest {
    @Test
    fun launchMainActivityTwentyTimes() {
        repeat(20) { i ->
            try {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    assertEquals(Lifecycle.State.RESUMED, scenario.state)
                }
                Log.i("STRESS", "iteration_$i: PASS")
            } catch (t: Throwable) {
                Log.e("STRESS", "iteration_$i: FAIL", t)
                throw t
            }
        }
    }
}
