package dev.hubops.core.model

/** Origem de um item no feed unificado. */
enum class Source { GITHUB, WORDPRESS, TERMINAL, DEVICE, DESPERTAR }

data class RepoDto(
    val fullName: String,
    val description: String,
    val stars: Int,
    val forks: Int,
    val language: String,
    val updatedAt: String,
    val isPrivate: Boolean,
    val url: String,
)

data class RepoResource(
    val kind: String,
    val title: String,
    val subtitle: String,
    val url: String,
)

data class CategoryDto(
    val id: Long,
    val name: String,
    val count: Int,
    val slug: String,
)

data class PostDto(
    val id: Long,
    val title: String,
    val excerpt: String,
    val link: String,
    val date: String,
)

/** Formato comum para a UI híbrida (resultado dos adapters). */
data class FeedItem(
    val source: Source,
    val title: String,
    val subtitle: String,
    val url: String?,
)

data class CommandResult(
    val command: String,
    val output: String,
    val exitCode: Int,
    val timedOut: Boolean,
    val truncated: Boolean,
)

data class DeviceInfo(
    val androidRelease: String,
    val sdk: Int,
    val model: String,
    val batteryPercent: Int,
    val batteryCharging: Boolean,
    val availableRamBytes: Long,
    val totalRamBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long,
    val uptimeMillis: Long,
    val termuxInstalled: Boolean,
    val shizukuInstalled: Boolean,
)
