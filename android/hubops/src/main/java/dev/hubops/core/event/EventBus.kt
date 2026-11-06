package dev.hubops.core.event

import dev.hubops.core.model.CommandResult
import dev.hubops.core.model.PostDto
import dev.hubops.core.model.RepoDto
import dev.hubops.core.model.Source
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface AppEvent {
    data class ReposLoaded(val items: List<RepoDto>) : AppEvent
    data class PostsLoaded(val items: List<PostDto>) : AppEvent
    data class PagesLoaded(val items: List<PostDto>) : AppEvent
    data class CommandFinished(val result: CommandResult) : AppEvent
    data class Failure(val source: Source, val message: String) : AppEvent
}

/** Barramento limitado com replay para não perder eventos durante a inicialização do consumidor. */
class EventBus {
    private val flow = MutableSharedFlow<AppEvent>(
        replay = 16,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<AppEvent> = flow.asSharedFlow()
    fun publish(event: AppEvent): Boolean = flow.tryEmit(event)
}
