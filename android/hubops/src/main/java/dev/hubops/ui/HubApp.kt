package dev.hubops.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hubops.ui.screens.DespertarScreen
import dev.hubops.ui.screens.FeedScreen
import dev.hubops.ui.screens.EspelharScreen
import dev.hubops.ui.screens.GitHubScreen
import dev.hubops.ui.screens.SettingsScreen
import dev.hubops.ui.screens.TerminalScreen
import dev.hubops.ui.screens.WordPressScreen
import dev.hubops.ui.screens.ProfessionalWorkflowScreen
import dev.hubops.ui.screens.OmegaScreen
import com.hubops.vislumbrops.ui.VerificationScreen

/** Router simples: cada rota é um módulo funcional. */
enum class Route(val label: String, val glyph: String) {
    FEED("Feed", "\u2630"),
    GITHUB("GitHub", "\u2325"),
    WORDPRESS("WordPress", "W"),
    TERMINAL("Terminal", "$"),
    DESPERTAR("Despertar", "\u2600"),
    ESPELHAR("Espelhar-X", "⟁"),
    VISLUMBOPS("VislumbOps", "◎"),
    PRO_WORKFLOW("Pro Flow", "✦"),
    OMEGA("Omega", "Ω"),
    SETTINGS("Config", "\u2699"),
}

@Composable
fun HubApp(vm: HubViewModel = viewModel()) {
    var routeName by rememberSaveable { mutableStateOf(Route.FEED.name) }
    val current = Route.valueOf(routeName)

    Scaffold(
        bottomBar = {
            NavigationBar {
                androidx.compose.foundation.layout.Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Route.values().forEach { r ->
                        NavigationBarItem(
                            modifier = Modifier.width(84.dp),
                            selected = r == current,
                            onClick = { routeName = r.name },
                            icon = { Text(r.glyph) },
                            label = { Text(r.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (current) {
                Route.FEED -> FeedScreen(vm)
                Route.GITHUB -> GitHubScreen(vm)
                Route.WORDPRESS -> WordPressScreen(vm)
                Route.TERMINAL -> TerminalScreen(vm)
                Route.DESPERTAR -> DespertarScreen(vm)
                Route.ESPELHAR -> EspelharScreen(vm)
                Route.VISLUMBOPS -> VerificationScreen()
                Route.PRO_WORKFLOW -> ProfessionalWorkflowScreen(vm)
                Route.OMEGA -> OmegaScreen(vm)
                Route.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}
