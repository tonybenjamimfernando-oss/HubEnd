package dev.hubops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.hubops.ui.HubViewModel

@Composable
fun TerminalScreen(vm: HubViewModel) {
    val history by vm.history.collectAsState()
    var cmd by rememberSaveable { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("Terminal local · sandbox Android") }
        item { Text("Diretório de trabalho: ${vm.terminalWorkingDirectory}") }
        item {
            Text("Executa comandos como a própria app, na pasta privada dela. Não tem root nem acesso automático ao armazenamento partilhado. Comandos podem alterar ou apagar ficheiros privados; execute apenas comandos que compreende. Tempo limite: 15 s.")
        }
        item {
            OutlinedTextField(
                value = cmd,
                onValueChange = { cmd = it },
                label = { Text("Comando (ex.: pwd, ls -la)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.runCommand(cmd) }, enabled = cmd.isNotBlank()) { Text("Executar") }
                Button(onClick = { vm.clearTerminalHistory() }, enabled = history.isNotEmpty()) { Text("Limpar histórico") }
            }
        }
        item { StatusLines(vm) }
        items(history.reversed()) { r ->
            val flags = buildString {
                append("código ${r.exitCode}")
                if (r.timedOut) append(" · tempo esgotado")
                if (r.truncated) append(" · saída truncada")
            }
            ItemCard(title = "$ ${r.command}", subtitle = flags)
            Text(r.output.ifBlank { "(sem saída)" }, fontFamily = FontFamily.Monospace)
        }
    }
}
