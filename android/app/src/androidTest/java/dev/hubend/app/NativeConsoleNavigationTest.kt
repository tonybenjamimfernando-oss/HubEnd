package dev.hubend.app

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hubops.ui.MainActivity as HubOpsConsoleActivity
import dev.hubops.ui.Route
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Abre a consola nativa HubOps e visita os 10 ecrãs em ART. Um fecho da aplicação em
 * qualquer ecrã termina o processo de teste e o teste falha.
 *
 * Cada separador tem de estar visível e com tamanho tocável: no 0.1.1 os separadores
 * tinham largura 0 (invisíveis no telemóvel) e o teste antigo passava na mesma.
 */
@RunWith(AndroidJUnit4::class)
class NativeConsoleNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<HubOpsConsoleActivity>()

    @Test
    fun everyConsoleScreenOpensWithoutCrashing() {
        Route.values().forEach { route ->
            val tab = compose.onNodeWithTagCompat("tab_${route.name}")
            tab.performScrollTo()
            tab.assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .performClick()
            compose.waitForIdle()
            tab.assertIsSelected()
            // Dá tempo aos efeitos de arranque de cada ecrã (leituras do dispositivo, ficheiros).
            Thread.sleep(1_500)
            compose.waitForIdle()
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onNodeWithTagCompat(tag: String) =
        onNode(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = false)
}
