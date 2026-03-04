package io.github.alelk.tgvd.features.download.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.job.CreateJobRequestDto
import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.api.contract.preview.PreviewRequestDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.preview.UserOverridesDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.features.common.component.ErrorCard
import io.github.alelk.tgvd.features.common.component.InfoRow
import io.github.alelk.tgvd.features.common.component.SectionCard
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.common.util.categoryLabel
import io.github.alelk.tgvd.features.common.util.formatDuration
import io.github.alelk.tgvd.features.generated.resources.Res
import io.github.alelk.tgvd.features.generated.resources.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val metadataTypes = CategoryDto.entries.toList()

class PreviewScreen(private val initialPreview: PreviewResponseDto) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val client = koinInject<TgVideoDownloaderClient>()
        val navigator = LocalNavigator.currentOrThrow

        var isCreating by remember { mutableStateOf(false) }
        var isRefreshing by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        // Current server response
        var preview by remember { mutableStateOf(initialPreview) }

        // Track which fields the user has manually edited
        val userEdits = remember { mutableStateSetOf<String>() }

        // --- Editable state initialized from preview ---
        var category by remember { mutableStateOf(preview.category) }
        var metadataType by remember {
            mutableStateOf(
                when (preview.metadata) {
                    is ResolvedMetadataDto.MusicVideo -> CategoryDto.MUSIC_VIDEO
                    is ResolvedMetadataDto.SeriesEpisode -> CategoryDto.SERIES_EPISODE
                    is ResolvedMetadataDto.Other -> CategoryDto.OTHER
                }
            )
        }
        var title by remember { mutableStateOf(preview.metadata.title) }
        var artist by remember { mutableStateOf((preview.metadata as? ResolvedMetadataDto.MusicVideo)?.artist ?: "") }
        var album by remember { mutableStateOf((preview.metadata as? ResolvedMetadataDto.MusicVideo)?.album ?: "") }
        var seriesName by remember { mutableStateOf((preview.metadata as? ResolvedMetadataDto.SeriesEpisode)?.seriesName ?: "") }
        var season by remember { mutableStateOf((preview.metadata as? ResolvedMetadataDto.SeriesEpisode)?.season ?: "") }
        var episode by remember { mutableStateOf((preview.metadata as? ResolvedMetadataDto.SeriesEpisode)?.episode ?: "") }
        var tags by remember { mutableStateOf(preview.metadata.tags.joinToString(", ")) }
        var originalPath by remember { mutableStateOf(preview.storagePlan.original.path) }
        var originalFormat by remember { mutableStateOf(preview.storagePlan.original.format) }
        val additionalOutputs = remember {
            mutableStateListOf(*preview.storagePlan.additional.map {
                OutputTargetDto(
                    path = it.path,
                    format = it.format,
                    maxQuality = it.maxQuality,
                    encodeSettings = it.encodeSettings,
                    embedThumbnail = it.embedThumbnail,
                    embedMetadata = it.embedMetadata,
                    embedSubtitles = it.embedSubtitles,
                    normalizeAudio = it.normalizeAudio,
                )
            }.toTypedArray())
        }

        // In-flight re-preview job (for cancellation)
        var rePreviewJob by remember { mutableStateOf<Job?>(null) }

        /** Build UserOverridesDto from current user edits */
        fun buildOverrides(): UserOverridesDto? {
            // Always send overrides when category changed, or any field was user-edited
            if ("category" !in userEdits && userEdits.isEmpty()) return null

            return when (metadataType) {
                CategoryDto.MUSIC_VIDEO -> UserOverridesDto.MusicVideo(
                    artist = artist.takeIf { "artist" in userEdits },
                    title = title.takeIf { "title" in userEdits },
                    album = album.takeIf { "album" in userEdits },
                )
                CategoryDto.SERIES_EPISODE -> UserOverridesDto.SeriesEpisode(
                    seriesName = seriesName.takeIf { "seriesName" in userEdits },
                    season = season.takeIf { "season" in userEdits },
                    episode = episode.takeIf { "episode" in userEdits },
                    title = title.takeIf { "title" in userEdits },
                )
                CategoryDto.OTHER -> UserOverridesDto.Other(
                    title = title.takeIf { "title" in userEdits },
                )
            }
        }

        /** Apply server response: update fields NOT in userEdits */
        fun applyServerResponse(response: PreviewResponseDto) {
            preview = response

            // Update category only if user didn't manually change it
            if ("category" !in userEdits) {
                category = response.category
                metadataType = when (response.metadata) {
                    is ResolvedMetadataDto.MusicVideo -> CategoryDto.MUSIC_VIDEO
                    is ResolvedMetadataDto.SeriesEpisode -> CategoryDto.SERIES_EPISODE
                    is ResolvedMetadataDto.Other -> CategoryDto.OTHER
                }
            }

            // Update metadata fields not in userEdits
            if ("title" !in userEdits) title = response.metadata.title
            if ("artist" !in userEdits) artist = (response.metadata as? ResolvedMetadataDto.MusicVideo)?.artist ?: ""
            if ("album" !in userEdits) album = (response.metadata as? ResolvedMetadataDto.MusicVideo)?.album ?: ""
            if ("seriesName" !in userEdits) seriesName = (response.metadata as? ResolvedMetadataDto.SeriesEpisode)?.seriesName ?: ""
            if ("season" !in userEdits) season = (response.metadata as? ResolvedMetadataDto.SeriesEpisode)?.season ?: ""
            if ("episode" !in userEdits) episode = (response.metadata as? ResolvedMetadataDto.SeriesEpisode)?.episode ?: ""
            if ("tags" !in userEdits) tags = response.metadata.tags.joinToString(", ")

            // Always update storage plan from server
            originalPath = response.storagePlan.original.path
            originalFormat = response.storagePlan.original.format
            additionalOutputs.clear()
            response.storagePlan.additional.forEach { additionalOutputs.add(it) }
        }

        /** Trigger re-preview with debounce */
        fun triggerRePreview(debounceMs: Long = 0L) {
            rePreviewJob?.cancel()
            rePreviewJob = scope.launch {
                if (debounceMs > 0) delay(debounceMs)
                val overrides = buildOverrides() ?: return@launch
                isRefreshing = true
                try {
                    val response = client.preview(
                        PreviewRequestDto(url = preview.source.url, overrides = overrides)
                    )
                    // Verify response matches our current overrides (race condition guard)
                    applyServerResponse(response)
                    errorMessage = null
                } catch (e: Exception) {
                    // Don't overwrite form on error — just show warning
                    errorMessage = "Re-preview failed: ${e.message}"
                } finally {
                    isRefreshing = false
                }
            }
        }

        fun buildMetadata(): ResolvedMetadataDto {
            val tagList = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            return when (metadataType) {
                CategoryDto.MUSIC_VIDEO -> ResolvedMetadataDto.MusicVideo(
                    artist = artist,
                    title = title,
                    album = album.takeIf { it.isNotBlank() },
                    tags = tagList,
                )
                CategoryDto.SERIES_EPISODE -> ResolvedMetadataDto.SeriesEpisode(
                    seriesName = seriesName,
                    season = season.takeIf { it.isNotBlank() },
                    episode = episode.takeIf { it.isNotBlank() },
                    title = title,
                    tags = tagList,
                )
                CategoryDto.OTHER -> ResolvedMetadataDto.Other(
                    title = title,
                    tags = tagList,
                )
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Preview")
                            if (isRefreshing) {
                                Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(TgvdIcons.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Video Info (read-only)
                SectionCard(title = "Video Info", icon = TgvdIcons.Videocam) {
                    InfoRow("Title", preview.videoInfo.title)
                    InfoRow("Channel", preview.videoInfo.channelName)
                    InfoRow("Duration", formatDuration(preview.videoInfo.durationSeconds))
                    InfoRow("Platform", preview.videoInfo.extractor)
                    preview.videoInfo.uploadDate?.let { InfoRow("Upload Date", it) }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Metadata (editable)
                SectionCard(
                    title = if (preview.matchedRule != null) "Metadata (rule: ${preview.matchedRule?.name ?: ""})" else "Metadata",
                    icon = TgvdIcons.Label,
                ) {
                    // Category / Metadata type selector (unified)
                    Text("Category", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        metadataTypes.forEachIndexed { index, type ->
                            SegmentedButton(
                                selected = metadataType == type,
                                onClick = {
                                    metadataType = type
                                    category = type
                                    userEdits.add("category")
                                    // Category change → immediate re-preview (0ms debounce)
                                    triggerRePreview(debounceMs = 0)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, metadataTypes.size),
                            ) {
                                Text(
                                    categoryLabel(type),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Title (always visible)
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            userEdits.add("title")
                            triggerRePreview(debounceMs = 700)
                        },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Type-specific fields
                    when (metadataType) {
                        CategoryDto.MUSIC_VIDEO -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = artist,
                                onValueChange = {
                                    artist = it
                                    userEdits.add("artist")
                                    triggerRePreview(debounceMs = 700)
                                },
                                label = { Text("Artist") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = album,
                                onValueChange = {
                                    album = it
                                    userEdits.add("album")
                                    triggerRePreview(debounceMs = 700)
                                },
                                label = { Text("Album (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        CategoryDto.SERIES_EPISODE -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = seriesName,
                                onValueChange = {
                                    seriesName = it
                                    userEdits.add("seriesName")
                                    triggerRePreview(debounceMs = 700)
                                },
                                label = { Text("Series Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = season,
                                    onValueChange = {
                                        season = it
                                        userEdits.add("season")
                                        triggerRePreview(debounceMs = 700)
                                    },
                                    label = { Text("Season") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = episode,
                                    onValueChange = {
                                        episode = it
                                        userEdits.add("episode")
                                        triggerRePreview(debounceMs = 700)
                                    },
                                    label = { Text("Episode") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        CategoryDto.OTHER -> { /* no additional fields */ }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tags,
                        onValueChange = {
                            tags = it
                            userEdits.add("tags")
                        },
                        label = { Text("Tags (comma-separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Storage Plan (editable paths for all outputs)
                SectionCard(title = "Storage Plan", icon = TgvdIcons.Folder) {
                    // Original output
                    Text("Original", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = originalPath,
                        onValueChange = { originalPath = it },
                        label = { Text("Output Path") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FormatDropdown(
                        selectedFormat = originalFormat,
                        onFormatSelected = { originalFormat = it },
                    )

                    // Additional outputs
                    additionalOutputs.forEachIndexed { index, target ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Output #${index + 2}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                target.format.serialized,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = target.path,
                            onValueChange = { newPath ->
                                additionalOutputs[index] = target.copy(path = newPath)
                            },
                            label = { Text("Output Path") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            minLines = 2,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Matched Rule (read-only info)
                preview.matchedRule?.let { rule ->
                    SectionCard(title = "Matched Rule", icon = TgvdIcons.Rule) {
                        InfoRow("Rule", rule.name ?: rule.id)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Metadata source
                SectionCard(title = "Source", icon = TgvdIcons.Label) {
                    InfoRow("Metadata source", preview.metadataSource.name)
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Warnings
                if (preview.warnings.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            preview.warnings.forEach { warning ->
                                Text(warning, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Error
                errorMessage?.let { error ->
                    ErrorCard(message = error)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Download Button
                Button(
                    onClick = {
                        isCreating = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val metadata = buildMetadata()
                                val storagePlan = StoragePlanDto(
                                    original = OutputTargetDto(
                                        path = originalPath,
                                        format = originalFormat,
                                        maxQuality = preview.storagePlan.original.maxQuality,
                                        encodeSettings = preview.storagePlan.original.encodeSettings,
                                        embedThumbnail = preview.storagePlan.original.embedThumbnail,
                                        embedMetadata = preview.storagePlan.original.embedMetadata,
                                        embedSubtitles = preview.storagePlan.original.embedSubtitles,
                                        normalizeAudio = preview.storagePlan.original.normalizeAudio,
                                    ),
                                    additional = additionalOutputs.toList(),
                                )
                                client.createJob(
                                    CreateJobRequestDto(
                                        source = preview.source,
                                        ruleId = preview.matchedRule?.id,
                                        category = category,
                                        videoInfo = preview.videoInfo,
                                        metadata = metadata,
                                        storagePlan = storagePlan,
                                    )
                                )
                                navigator.pop()
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Failed to create job"
                            } finally {
                                isCreating = false
                            }
                        }
                    },
                    enabled = !isCreating && !isRefreshing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isCreating) "Creating..." else "Download")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun mutableStateSetOf(vararg elements: String): MutableSet<String> {
    // Simple mutable set backed by mutableStateListOf for compose recomposition
    return remember { mutableSetOf(*elements) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatDropdown(
    selectedFormat: OutputFormatDto,
    onFormatSelected: (OutputFormatDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedFormat.serialized,
            onValueChange = {},
            readOnly = true,
            label = { Text("Format") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            OutputFormatDto.allFormats.forEach { format ->
                DropdownMenuItem(
                    text = { Text(format.serialized) },
                    onClick = {
                        onFormatSelected(format)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
