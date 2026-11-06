package dev.hubops.despertar.project

/**
 * HubEnd: modelos de aplicação do Project Forge. Cada modelo gera uma app funcional (não um ecrã vazio):
 * notas e tarefas guardadas no telemóvel, ou uma app que abre um site. Todo o código gerado é original.
 */
enum class ProjectTemplate(val label: String, val description: String) {
    BASIC("Ecrã inicial", "ecrã de boas-vindas em Jetpack Compose, ponto de partida limpo."),
    NOTES("Bloco de notas", "criar, ver e apagar notas, guardadas no telemóvel mesmo depois de fechar a app."),
    TASKS("Lista de tarefas", "adicionar tarefas, marcar como feitas e limpar as concluídas, com contador e gravação local."),
    WEB("App de site", "abre o seu site numa app própria, com botão voltar e ecrã de erro sem Internet."),
}

internal object ProjectTemplates {

    fun mainActivity(b: AndroidProjectBlueprint): String = when (b.template) {
        ProjectTemplate.BASIC -> basic(b)
        ProjectTemplate.NOTES -> notes(b)
        ProjectTemplate.TASKS -> tasks(b)
        ProjectTemplate.WEB -> web(b)
    }

    private fun kt(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

    private fun basic(b: AndroidProjectBlueprint) = """package ${b.packageName}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StarterScreen() }
    }
}

@Composable
private fun StarterScreen() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${kt(b.name)}", style = MaterialTheme.typography.headlineMedium)
                Text("Projeto Android criado pelo HubOps Despertar")
            }
        }
    }
}
"""

    /** Imports comuns aos modelos com lista guardada no telemóvel. */
    private val listImports = """import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp"""

    private val storage = """
/** Guarda a lista nas preferências privadas da app (sobrevive a fechar e reiniciar). */
private class ItemStorage(context: Context) {
    private val prefs = context.getSharedPreferences("items", Context.MODE_PRIVATE)
    fun load(): List<Item> = ItemCodec.decode(prefs.getString("data", "").orEmpty())
    fun save(items: List<Item>) { prefs.edit().putString("data", ItemCodec.encode(items)).apply() }
}
"""

    private fun notes(b: AndroidProjectBlueprint) = """package ${b.packageName}

$listImports

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = ItemStorage(this)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { NotesScreen(storage) } } }
    }
}
$storage
@Composable
private fun NotesScreen(storage: ItemStorage) {
    var notes by remember { mutableStateOf(storage.load()) }
    var draft by remember { mutableStateOf("") }
    fun update(next: List<Item>) { notes = next; storage.save(next) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${kt(b.name)}", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = draft, onValueChange = { draft = it },
            label = { Text("Nova nota") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = draft.isNotBlank(),
            onClick = { update(ItemCodec.add(notes, draft)); draft = "" },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar nota") }
        Text("Notas: " + notes.size)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(notes.reversed(), key = { it.id }) { note ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(note.text)
                        TextButton(onClick = { update(ItemCodec.remove(notes, note.id)) }) { Text("Apagar") }
                    }
                }
            }
        }
    }
}
"""

    private fun tasks(b: AndroidProjectBlueprint) = """package ${b.packageName}

$listImports

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = ItemStorage(this)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { TasksScreen(storage) } } }
    }
}
$storage
@Composable
private fun TasksScreen(storage: ItemStorage) {
    var tasks by remember { mutableStateOf(storage.load()) }
    var draft by remember { mutableStateOf("") }
    fun update(next: List<Item>) { tasks = next; storage.save(next) }
    val done = tasks.count { it.done }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${kt(b.name)}", style = MaterialTheme.typography.headlineMedium)
        Text("Feitas: " + done + " de " + tasks.size)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it }, singleLine = true,
                label = { Text("Nova tarefa") }, modifier = Modifier.weight(1f),
            )
            Button(enabled = draft.isNotBlank(), onClick = { update(ItemCodec.add(tasks, draft)); draft = "" }) { Text("Adicionar") }
        }
        TextButton(enabled = done > 0, onClick = { update(ItemCodec.clearDone(tasks)) }) { Text("Limpar concluídas") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(tasks, key = { it.id }) { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = task.done, onCheckedChange = { update(ItemCodec.toggle(tasks, task.id)) })
                    Text(
                        task.text,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { update(ItemCodec.remove(tasks, task.id)) }) { Text("Apagar") }
                }
            }
        }
    }
}
"""

    private fun web(b: AndroidProjectBlueprint) = """package ${b.packageName}

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

const val START_URL = "${kt(b.startUrl)}"

class MainActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var failed by mutableStateOf(false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val view = webView
                if (view != null && view.canGoBack()) view.goBack() else finish()
            }
        })
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (failed) {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Sem ligação à Internet", style = MaterialTheme.typography.titleLarge)
                            Button(onClick = { failed = false; webView?.reload() }) { Text("Tentar de novo") }
                        }
                    }
                    AndroidView(
                        modifier = if (failed) Modifier else Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = object : WebViewClient() {
                                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                        if (request.isForMainFrame) failed = true
                                    }
                                }
                                if (savedInstanceState == null) loadUrl(START_URL)
                                webView = this
                            }
                        },
                    )
                }
            }
        }
    }
}
"""

    /** Lógica pura (sem Android) para a lista: testada por ItemCodecTest no próprio projeto gerado. */
    fun itemCodec(b: AndroidProjectBlueprint) = """package ${b.packageName}

import java.util.Base64

data class Item(val id: Long, val text: String, val done: Boolean = false)

/** Formato estável: uma linha por item "id|0ou1|texto em Base64". Linhas inválidas são ignoradas. */
object ItemCodec {
    fun encode(items: List<Item>): String = items.joinToString("\n") { item ->
        val text = Base64.getEncoder().encodeToString(item.text.toByteArray(Charsets.UTF_8))
        item.id.toString() + "|" + (if (item.done) "1" else "0") + "|" + text
    }

    fun decode(raw: String): List<Item> = raw.lineSequence().mapNotNull { line ->
        val parts = line.split("|")
        if (parts.size != 3) return@mapNotNull null
        val id = parts[0].toLongOrNull() ?: return@mapNotNull null
        val text = try { String(Base64.getDecoder().decode(parts[2]), Charsets.UTF_8) } catch (_: IllegalArgumentException) { return@mapNotNull null }
        Item(id, text, parts[1] == "1")
    }.toList()

    fun add(items: List<Item>, text: String): List<Item> {
        val clean = text.trim()
        if (clean.isEmpty()) return items
        val nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1L
        return items + Item(nextId, clean)
    }

    fun toggle(items: List<Item>, id: Long) = items.map { if (it.id == id) it.copy(done = !it.done) else it }
    fun remove(items: List<Item>, id: Long) = items.filterNot { it.id == id }
    fun clearDone(items: List<Item>) = items.filterNot { it.done }
}
"""

    fun itemCodecTest(b: AndroidProjectBlueprint) = """package ${b.packageName}

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemCodecTest {
    @Test fun keepsTextWithSeparatorsAndAccentsAfterSaving() {
        val items = ItemCodec.add(ItemCodec.add(emptyList(), "Comprar pão | leite"), "Linha 1\nLinha 2")
        assertEquals(items, ItemCodec.decode(ItemCodec.encode(items)))
    }

    @Test fun ignoresEmptyTextAndCorruptLines() {
        assertTrue(ItemCodec.add(emptyList(), "   ").isEmpty())
        assertTrue(ItemCodec.decode("lixo\n1|0|%%%").isEmpty())
    }

    @Test fun toggleAndClearDoneKeepPendingItems() {
        val items = ItemCodec.add(ItemCodec.add(emptyList(), "A"), "B")
        val toggled = ItemCodec.toggle(items, items.first().id)
        assertEquals(listOf("B"), ItemCodec.clearDone(toggled).map { it.text })
    }

    @Test fun newIdsAreUniqueAfterRemoval() {
        val items = ItemCodec.add(ItemCodec.add(emptyList(), "A"), "B")
        val after = ItemCodec.add(ItemCodec.remove(items, 1L), "C")
        assertEquals(after.map { it.id }.toSet().size, after.size)
    }
}
"""

    /** Compila e testa o projeto no GitHub Actions; o APK fica como artefacto descarregável. */
    fun githubWorkflow() = """name: Android APK

on:
  push:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: android-actions/setup-android@v3
        with:
          packages: "platform-tools platforms;android-34 build-tools;34.0.0"
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "8.11.1"
      - name: Testes e APK debug
        run: gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: apk-debug
          path: app/build/outputs/apk/debug/app-debug.apk
"""
}
