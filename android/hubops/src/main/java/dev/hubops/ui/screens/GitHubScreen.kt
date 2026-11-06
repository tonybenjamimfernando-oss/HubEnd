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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.hubops.ui.HubViewModel

@Composable
fun GitHubScreen(vm: HubViewModel) {
    val repos by vm.repos.collectAsState()
    val resources by vm.repoResources.collectAsState()
    val selectedRepo by vm.selectedRepo.collectAsState()
    val uri = LocalUriHandler.current
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf(1) }
    var mode by rememberSaveable { mutableStateOf("search") }
    var resourceKind by rememberSaveable { mutableStateOf("branches") }
    var resourcePage by rememberSaveable { mutableStateOf(1) }
    val pageSize = if (mode == "search") 20 else 30

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("GitHub · Exploração de repositórios") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; page = 1 },
                label = { Text("Pesquisa ou nome de utilizador") },
                supportingText = { Text("Aceita qualificadores GitHub, como language:kotlin, stars:>100 e topic:android.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { mode = "search"; page = 1; vm.searchRepos(query, 1) }) { Text("Pesquisar") }
                Button(onClick = { mode = "user"; page = 1; vm.userRepos(query, 1) }) { Text("Por utilizador") }
            }
        }
        item {
            Button(onClick = { mode = "mine"; page = 1; vm.myRepos(1) }) {
                Text("Os meus repositórios (token)")
            }
        }
        item { StatusLines(vm) }
        if (repos.isNotEmpty()) {
            item {
                Text("${repos.size} repositórios nesta página · página $page")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = page > 1, onClick = {
                        page -= 1
                        when (mode) {
                            "search" -> vm.searchRepos(query, page)
                            "mine" -> vm.myRepos(page)
                            else -> vm.userRepos(query, page)
                        }
                    }) { Text("Anterior") }
                    Button(enabled = repos.size == pageSize && page < 100, onClick = {
                        page += 1
                        when (mode) {
                            "search" -> vm.searchRepos(query, page)
                            "mine" -> vm.myRepos(page)
                            else -> vm.userRepos(query, page)
                        }
                    }) { Text("Seguinte") }
                }
            }
        }
        items(repos, key = { it.fullName }) { r ->
            val lang = if (r.language.isBlank()) "" else " · ${r.language}"
            ItemCard(
                title = r.fullName,
                subtitle = "${r.description}\n★ ${r.stars} · Forks ${r.forks}$lang\n${if (r.isPrivate) "Privado" else "Público"} · Atualizado: ${r.updatedAt.ifBlank { "sem data" }}",
                onClick = r.url.takeIf { it.startsWith("https://") }?.let { u -> { uri.openUri(u) } },
            )
            Button(onClick = { resourceKind = "branches"; resourcePage = 1; vm.loadRepoResources(r.fullName, resourceKind, resourcePage) }) { Text("Explorar ${r.fullName}") }
        }
        if (selectedRepo != null) {
            item { ScreenTitle("Explorador · $selectedRepo") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { resourceKind = "branches"; resourcePage = 1; vm.loadRepoResources(selectedRepo!!, resourceKind, resourcePage) }) { Text("Branches") }
                    Button(onClick = { resourceKind = "commits"; resourcePage = 1; vm.loadRepoResources(selectedRepo!!, resourceKind, resourcePage) }) { Text("Commits") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { resourceKind = "releases"; resourcePage = 1; vm.loadRepoResources(selectedRepo!!, resourceKind, resourcePage) }) { Text("Releases") }
                    Button(onClick = { resourceKind = "issues"; resourcePage = 1; vm.loadRepoResources(selectedRepo!!, resourceKind, resourcePage) }) { Text("Issues") }
                }
            }
            item {
                Text("$resourceKind · página $resourcePage")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = resourcePage > 1, onClick = {
                        resourcePage -= 1
                        vm.loadRepoResources(selectedRepo!!, resourceKind, resourcePage)
                    }) { Text("Anterior") }
                    Button(enabled = resources.size == 20 && resourcePage < 100, onClick = {
                        resourcePage += 1
                        vm.loadRepoResources(selectedRepo!!, resourceKind, resourcePage)
                    }) { Text("Seguinte") }
                }
            }
            if (resources.isEmpty()) item { Text("Sem elementos nesta categoria ou ainda não carregados.") }
            items(resources) { resource ->
                ItemCard(
                    title = resource.title,
                    subtitle = resource.subtitle,
                    onClick = resource.url.takeIf { it.startsWith("https://github.com/") }?.let { link -> { uri.openUri(link) } },
                )
            }
        }
    }
}
