package dev.hubops.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        bottomBar = { ConsoleTabBar(current) { routeName = it.name } },
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

/**
 * HubEnd: barra de separadores com rolagem horizontal. O original colocava
 * NavigationBarItem (que usa weight(1f)) dentro de um Row com horizontalScroll; com
 * largura ilimitada o peso dá largura 0 e os 10 separadores ficavam invisíveis no
 * telemóvel, deixando a consola presa no Feed.
 */
@Composable
private fun ConsoleTabBar(current: Route, onSelect: (Route) -> Unit) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Route.values().forEach { r ->
                val selected = r == current
                Column(
                    Modifier
                        .testTag("tab_${r.name}")
                        .width(76.dp)
                        .height(60.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(14.dp),
                        )
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(r) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(r.glyph, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        r.label,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
