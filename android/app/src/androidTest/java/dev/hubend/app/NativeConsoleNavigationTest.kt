package dev.hubend.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hubops.ui.MainActivity as HubOpsConsoleActivity
import dev.hubops.ui.Route
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Abre a consola nativa HubOps e visita os 10 ecrãs em ART. Um fecho da aplicação em
 * qualquer ecrã termina o processo de teste e o teste falha.
 */
@RunWith(AndroidJUnit4::class)
class NativeConsoleNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<HubOpsConsoleActivity>()

    @Test
    fun everyConsoleScreenOpensWithoutCrashing() {
        Route.values().forEach { route ->
            compose.onAllNodes(hasText(route.label) and isSelectable()).onFirst()
                .performScrollTo()
                .performClick()
            compose.waitForIdle()
            // Dá tempo aos efeitos de arranque de cada ecrã (leituras do dispositivo, ficheiros).
            Thread.sleep(1_500)
            compose.waitForIdle()
        }
    }
}
