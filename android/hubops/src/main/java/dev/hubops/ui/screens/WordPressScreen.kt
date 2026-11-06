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
fun WordPressScreen(vm: HubViewModel) {
    val posts by vm.posts.collectAsState()
    val pages by vm.pages.collectAsState()
    val categories by vm.categories.collectAsState()
    val uri = LocalUriHandler.current
    var site by rememberSaveable { mutableStateOf(vm.config.wpSite) }
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf(1) }
    var viewMode by rememberSaveable { mutableStateOf("posts") }
    var categoryPage by rememberSaveable { mutableStateOf(1) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("WordPress · Conteúdo e rascunhos") }
        item {
            OutlinedTextField(
                value = site,
                onValueChange = { site = it; page = 1 },
                label = { Text("Site (https://exemplo.com)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { viewMode = "posts"; page = 1; vm.loadPosts(site, 1) }) { Text("Publicações") }
                Button(onClick = { viewMode = "pages"; page = 1; vm.loadPages(site, 1) }) { Text("Páginas") }
                Button(onClick = { viewMode = "categories"; categoryPage = 1; vm.loadCategories(site, 1) }) { Text("Categorias") }
            }
        }
        item { Text("Criar rascunho (não será publicado automaticamente)") }
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título do rascunho") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Conteúdo (texto ou HTML)") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                enabled = title.isNotBlank() && content.isNotBlank(),
                onClick = { vm.createDraft(site, title, content) },
            ) { Text("Guardar como rascunho") }
        }
        item { StatusLines(vm) }
        val currentItems = if (viewMode == "pages") pages else posts
        if (viewMode != "categories" && currentItems.isNotEmpty()) {
            item {
                Text("${currentItems.size} ${if (viewMode == "pages") "páginas" else "publicações"} · página $page")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = page > 1, onClick = {
                        page -= 1
                        if (viewMode == "pages") vm.loadPages(site, page) else vm.loadPosts(site, page)
                    }) { Text("Anterior") }
                    Button(enabled = currentItems.size == 10 && page < 1000, onClick = {
                        page += 1
                        if (viewMode == "pages") vm.loadPages(site, page) else vm.loadPosts(site, page)
                    }) { Text("Seguinte") }
                }
            }
        }
        if (viewMode == "categories") {
            item {
                Text("${categories.size} categorias · página $categoryPage")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = categoryPage > 1, onClick = { categoryPage -= 1; vm.loadCategories(site, categoryPage) }) { Text("Anterior") }
                    Button(enabled = categories.size == 50 && categoryPage < 1000, onClick = { categoryPage += 1; vm.loadCategories(site, categoryPage) }) { Text("Seguinte") }
                }
            }
            items(categories, key = { it.id }) { category ->
                ItemCard(title = category.name, subtitle = "${category.count} publicações · ${category.slug}")
            }
        } else {
            items(currentItems, key = { it.id }) { p ->
                ItemCard(
                    title = p.title.ifBlank { "(sem título)" },
                    subtitle = "${p.date}\n${p.excerpt}",
                    onClick = p.link.takeIf { it.startsWith("https://") }?.let { l -> { uri.openUri(l) } },
                )
            }
        }
    }
}
