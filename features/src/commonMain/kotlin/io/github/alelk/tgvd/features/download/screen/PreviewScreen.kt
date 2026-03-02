package io.github.alelk.tgvd.features.download.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.job.CreateJobRequestDto
import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.features.common.component.ErrorCard
import io.github.alelk.tgvd.features.common.component.InfoRow
import io.github.alelk.tgvd.features.common.component.SectionCard
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.common.util.formatDuration
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val metadataTypes = listOf("other", "music-video", "series-episode")

private val availableFormats = listOf(
    "original/mp4", "original/mkv", "original/webm", "original/avi", "original/mov",
    "video/mp4", "video/mkv", "video/webm", "video/avi", "video/mov",
    "audio/m4a", "audio/mp3", "audio/opus", "audio/flac", "audio/wav",
    "image/jpg", "image/png", "image/webp",
)

class PreviewScreen(private val preview: PreviewResponseDto) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val client = koinInject<TgVideoDownloaderClient>()
        val navigator = LocalNavigator.currentOrThrow

        var isCreating by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        // --- Editable state initialized from preview ---
        var category by remember { mutableStateOf(preview.category) }
        var metadataType by remember {
            mutableStateOf(
                when (preview.metadata) {
                    is ResolvedMetadataDto.MusicVideo -> "music-video"
                    is ResolvedMetadataDto.SeriesEpisode -> "series-episode"
                    is ResolvedMetadataDto.Other -> "other"
                }
            )
        }
        var title by remember { mutableStateOf(preview.metadata.title) }
        var artist by remember { mutableStateOf((preview.metadata as? ResolvedMetadataDto.MusicVideo)?.artist ?: "") }
        var seriesName by remember { mutableStateOf((preview.metadata as? ResolvedMetadataDto.SeriesEpisode)?.seriesName ?: "") }
        var season by remember { mutableStateOf((preview.metadata as? ResolvedMetadataDto.SeriesEpisode)?.season ?: "") }
        var episode by remember { mutableStateOf((preview.metadata as? ResolvedMetadataDto.SeriesEpisode)?.episode ?: "") }
        var tags by remember { mutableStateOf(preview.metadata.tags.joinToString(", ")) }
        var originalPath by remember { mutableStateOf(preview.storagePlan.original.path) }
        var originalFormat by remember { mutableStateOf(preview.storagePlan.original.format) }

        fun buildMetadata(): ResolvedMetadataDto {
            val tagList = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            return when (metadataType) {
                "music-video" -> ResolvedMetadataDto.MusicVideo(
                    artist = artist,
                    title = title,
                    tags = tagList,
                )
                "series-episode" -> ResolvedMetadataDto.SeriesEpisode(
                    seriesName = seriesName,
                    season = season.takeIf { it.isNotBlank() },
                    episode = episode.takeIf { it.isNotBlank() },
                    title = title,
                    tags = tagList,
                )
                else -> ResolvedMetadataDto.Other(
                    title = title,
                    tags = tagList,
                )
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Preview") },
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
                    title = if (preview.matchedRule != null) "Metadata (rule)" else "Metadata",
                    icon = TgvdIcons.Label,
                ) {
                    // Category
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Metadata type selector
                    Text("Type", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        metadataTypes.forEachIndexed { index, type ->
                            SegmentedButton(
                                selected = metadataType == type,
                                onClick = { metadataType = type },
                                shape = SegmentedButtonDefaults.itemShape(index, metadataTypes.size),
                            ) {
                                Text(
                                    when (type) {
                                        "music-video" -> "Music"
                                        "series-episode" -> "Series"
                                        else -> "Other"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Title (always visible)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Type-specific fields
                    when (metadataType) {
                        "music-video" -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = artist,
                                onValueChange = { artist = it },
                                label = { Text("Artist") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        "series-episode" -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = seriesName,
                                onValueChange = { seriesName = it },
                                label = { Text("Series Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = season,
                                    onValueChange = { season = it },
                                    label = { Text("Season") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = episode,
                                    onValueChange = { episode = it },
                                    label = { Text("Episode") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Tags (comma-separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Storage Plan (editable path)
                SectionCard(title = "Storage Plan", icon = TgvdIcons.Folder) {
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
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Matched Rule (read-only info)
                preview.matchedRule?.let { rule ->
                    SectionCard(title = "Matched Rule", icon = TgvdIcons.Rule) {
                        InfoRow("Rule", rule.name ?: rule.id)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

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
                                    original = OutputTargetDto(path = originalPath, format = originalFormat),
                                    additional = preview.storagePlan.additional,
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
                    enabled = !isCreating,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatDropdown(
    selectedFormat: String,
    onFormatSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedFormat,
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
            availableFormats.forEach { format ->
                DropdownMenuItem(
                    text = { Text(format) },
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

