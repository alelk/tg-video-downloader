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
import io.github.alelk.tgvd.features.common.component.ErrorCard
import io.github.alelk.tgvd.features.common.component.InfoRow
import io.github.alelk.tgvd.features.common.component.SectionCard
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.common.util.formatDuration
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class PreviewScreen(private val preview: PreviewResponseDto) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val client = koinInject<TgVideoDownloaderClient>()
        val navigator = LocalNavigator.currentOrThrow

        var isCreating by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

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
                // Video Info
                SectionCard(title = "Video Info", icon = TgvdIcons.Videocam) {
                    InfoRow("Title", preview.videoInfo.title)
                    InfoRow("Channel", preview.videoInfo.channelName)
                    InfoRow("Duration", formatDuration(preview.videoInfo.durationSeconds))
                    InfoRow("Platform", preview.videoInfo.extractor)
                    preview.videoInfo.uploadDate?.let { InfoRow("Upload Date", it) }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Metadata
                SectionCard(title = "Metadata (${preview.metadataSource.name.lowercase()})", icon = TgvdIcons.Label) {
                    InfoRow("Category", preview.category)
                    when (val meta = preview.metadata) {
                        is ResolvedMetadataDto.MusicVideo -> {
                            InfoRow("Artist", meta.artist)
                            InfoRow("Title", meta.title)
                        }
                        is ResolvedMetadataDto.SeriesEpisode -> {
                            InfoRow("Series", meta.seriesName)
                            meta.season?.let { InfoRow("Season", it) }
                            meta.episode?.let { InfoRow("Episode", it) }
                            InfoRow("Title", meta.title)
                        }
                        is ResolvedMetadataDto.Other -> {
                            InfoRow("Title", meta.title)
                        }
                    }
                    if (preview.metadata.tags.isNotEmpty()) {
                        InfoRow("Tags", preview.metadata.tags.joinToString(", "))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Storage Plan
                SectionCard(title = "Storage Plan", icon = TgvdIcons.Folder) {
                    Text("Original: ${preview.storagePlan.original.format}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        preview.storagePlan.original.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    preview.storagePlan.additional.forEach { target ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Additional: ${target.format}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            target.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Matched Rule
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
                                client.createJob(
                                    CreateJobRequestDto(
                                        source = preview.source,
                                        ruleId = preview.matchedRule?.id,
                                        category = preview.category,
                                        videoInfo = preview.videoInfo,
                                        metadata = preview.metadata,
                                        storagePlan = preview.storagePlan,
                                    )
                                )
                                // Go back to URL input after job created
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


