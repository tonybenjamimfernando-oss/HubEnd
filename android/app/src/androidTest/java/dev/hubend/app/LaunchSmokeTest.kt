package dev.hubend.app

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hubops.HubOpsApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teste de arranque em ART (emulador/dispositivo): a aplicação HubOps inicializa os motores
 * e a atividade HubEnd chega ao estado RESUMED sem fechar.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @Test
    fun applicationInitialisesEngines() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(app is HubOpsApplication)
        assertNotNull((app as HubOpsApplication).omegaEngines.snse)
    }

    @Test
    fun mainActivityReachesResumed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
