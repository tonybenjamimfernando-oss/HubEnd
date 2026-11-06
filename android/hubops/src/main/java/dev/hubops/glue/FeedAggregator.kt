package dev.hubops.glue

import dev.hubops.core.event.AppEvent
import dev.hubops.core.event.EventBus
import dev.hubops.core.model.FeedItem
import dev.hubops.core.model.Source
import dev.hubops.core.model.toFeedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Código "cola": escuta o EventBus e funde todas as fontes num feed unificado + registo. */
class FeedAggregator(bus: EventBus, scope: CoroutineScope) {

    private val _feed = MutableStateFlow<List<FeedItem>>(emptyList())
    val feed: StateFlow<List<FeedItem>> = _feed.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        scope.launch { bus.events.collect { handle(it) } }
    }

    fun clear() {
        _feed.value = emptyList()
        _log.value = emptyList()
        _lastError.value = null
    }

    private fun handle(event: AppEvent) {
        when (event) {
            is AppEvent.ReposLoaded -> {
                replace(Source.GITHUB, event.items.map { it.toFeedItem() })
                addLog("GitHub: ${event.items.size} repositórios")
                _lastError.value = null
            }
            is AppEvent.PostsLoaded -> {
                replace(Source.WORDPRESS, event.items.map { it.toFeedItem() })
                addLog("WordPress: ${event.items.size} posts")
                _lastError.value = null
            }
            is AppEvent.PagesLoaded -> {
                replace(Source.WORDPRESS, event.items.map { it.toFeedItem() })
                addLog("WordPress: ${event.items.size} páginas")
                _lastError.value = null
            }
            is AppEvent.CommandFinished -> {
                _lastError.value = null
                val item = event.result.toFeedItem()
                _feed.update { cur ->
                    val others = cur.filter { it.source != Source.TERMINAL }
                    val terminal = (cur.filter { it.source == Source.TERMINAL } + item).takeLast(5)
                    others + terminal
                }
                addLog("Terminal: comando executado (${item.subtitle})")
            }
            is AppEvent.Failure -> {
                _lastError.value = event.message
                addLog("Erro ${event.source}: ${event.message}")
            }
        }
    }

    private fun replace(source: Source, items: List<FeedItem>) {
        _feed.update { cur -> (cur.filter { it.source != source } + items).takeLast(100) }
    }

    private fun addLog(line: String) {
        _log.update { (it + line).takeLast(50) }
    }
}
