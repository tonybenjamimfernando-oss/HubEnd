package dev.hubend.app

import android.os.StrictMode
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.hubops.HubOpsApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Teste de arranque em ART (emulador/dispositivo): a aplicação HubOps inicializa os motores
 * e a atividade HubEnd chega ao estado RESUMED sem fechar.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @get:Rule
    val timeout: TestRule = Timeout.seconds(10)

    private var activityScenario: ActivityScenario<MainActivity>? = null

    @Test
    fun applicationInitialisesEngines() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(app is HubOpsApplication)
        assertNotNull((app as HubOpsApplication).omegaEngines.snse)
    }

    @Test
    fun mainActivityReachesResumed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var originalMainThreadPolicy: StrictMode.ThreadPolicy? = null

        instrumentation.runOnMainSync {
            originalMainThreadPolicy = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder(originalMainThreadPolicy)
                    .detectNetwork()
                    .penaltyLog()
                    .penaltyDeathOnNetwork()
                    .build()
            )
        }

        try {
            activityScenario = ActivityScenario.launch(MainActivity::class.java)
            assertEquals(Lifecycle.State.RESUMED, activityScenario?.state)
        } catch (failure: Throwable) {
            Log.e(TAG, "Activity launch failed", failure)
            throw failure
        } finally {
            instrumentation.runOnMainSync {
                originalMainThreadPolicy?.let(StrictMode::setThreadPolicy)
            }
        }
    }

    @After
    fun captureActivityStateAfterTest() {
        val state = runCatching { activityScenario?.state?.name ?: "NOT_LAUNCHED" }
            .getOrElse { "STATE_READ_FAILED: ${it.javaClass.name}: ${it.message}" }
        Log.i(TAG, "ActivityScenario state in @After: $state")
        runCatching { activityScenario?.close() }
            .onFailure { Log.w(TAG, "ActivityScenario close failed", it) }
    }

    private companion object {
        const val TAG = "HubEndLaunchSmokeTest"
    }
}
