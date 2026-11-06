package dev.hubops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.hubops.ui.HubViewModel

@Composable
fun SettingsScreen(vm: HubViewModel) {
    var token by remember { mutableStateOf(vm.config.githubToken) }
    var site by remember { mutableStateOf(vm.config.wpSite) }
    var user by remember { mutableStateOf(vm.config.wpUser) }
    var pass by remember { mutableStateOf(vm.config.wpAppPassword) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenTitle("Configura\u00e7\u00e3o")
        Text("Os valores ficam cifrados no dispositivo (Android Keystore). Nada \u00e9 embutido no c\u00f3digo.")
        OutlinedTextField(
            value = token, onValueChange = { token = it },
            label = { Text("Token GitHub (opcional)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = site, onValueChange = { site = it },
            label = { Text("Site WordPress (https://...)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = user, onValueChange = { user = it },
            label = { Text("Utilizador WordPress") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("Application Password WordPress") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.saveSettings(token, site, user, pass) }) { Text("Guardar") }
        var dev by remember { mutableStateOf(vm.config.devUnlocked) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = dev, onCheckedChange = { dev = it; vm.setDevUnlocked(it) })
            Text("  Desbloquear Despertar (modo desenvolvimento)")
        }
        StatusLines(vm)
    }
}
