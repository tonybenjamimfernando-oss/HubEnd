package dev.hubops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hubops.ui.HubViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun ScreenTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

@Composable
fun ItemCard(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    val mod = Modifier.fillMaxWidth()
    Card(
        modifier = if (onClick != null) mod.then(Modifier.clickableCompat(onClick)) else mod,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Mostra erro/aviso global (vindos do glue e do ViewModel). */
@Composable
fun StatusLines(vm: HubViewModel) {
    val error by vm.aggregator.lastError.collectAsState()
    val notice by vm.notice.collectAsState()
    val busy by vm.busy.collectAsState()
    if (busy) Text("A processar\u2026")
    error?.let { Text("Erro: $it", color = MaterialTheme.colorScheme.error) }
    notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
}
