package dev.hubops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.hubops.ui.HubViewModel

@Composable
fun FeedScreen(vm: HubViewModel) {
    val feed by vm.aggregator.feed.collectAsState()
    val log by vm.aggregator.log.collectAsState()
    val info by vm.deviceInfo.collectAsState()
    val uri = LocalUriHandler.current

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("Feed unificado") }
        item {
            info?.let { d ->
                val gb = 1024.0 * 1024.0 * 1024.0
                val uptimeHours = d.uptimeMillis / 3_600_000.0
                ItemCard(
                    title = "${d.model} \u00b7 Android ${d.androidRelease} (API ${d.sdk})",
                    subtitle = "Bateria ${if (d.batteryPercent >= 0) "${d.batteryPercent}%" else "indisponível"}${if (d.batteryCharging) " · a carregar" else ""}\nArmazenamento livre %.1f / %.1f GB\nRAM disponível %.1f / %.1f GB\nTempo desde arranque %.1f h\nTermux: %s · Shizuku: %s".format(
                        d.freeBytes / gb, d.totalBytes / gb,
                        d.availableRamBytes / gb, d.totalRamBytes / gb,
                        uptimeHours,
                        if (d.termuxInstalled) "instalado" else "não instalado",
                        if (d.shizukuInstalled) "instalado" else "não instalado",
                    ),
                )
            }
        }
        item { Button(onClick = { vm.refreshDevice() }) { Text("Atualizar diagn\u00f3stico") } }
        if (feed.isEmpty()) {
            item { Text("Sem itens. Carregue dados nos separadores GitHub, WordPress ou Terminal.") }
        }
        items(feed) { f ->
            val link = f.url?.takeIf { it.startsWith("https://") }
            ItemCard(
                title = "[${f.source}] ${f.title}",
                subtitle = f.subtitle,
                onClick = link?.let { l -> { uri.openUri(l) } },
            )
        }
        if (log.isNotEmpty()) {
            item { Text("Registo de atividade") }
            items(log.reversed()) { Text(it) }
        }
    }
}
