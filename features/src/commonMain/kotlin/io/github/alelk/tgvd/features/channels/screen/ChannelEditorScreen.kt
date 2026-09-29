package io.github.alelk.tgvd.features.channels.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.channel.CreateChannelDto
import io.github.alelk.tgvd.api.contract.channel.UpdateChannelDto
import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import io.github.alelk.tgvd.features.common.component.ErrorCard
import io.github.alelk.tgvd.features.common.component.SectionCard
import io.github.alelk.tgvd.features.common.component.TrackPreferencesEditor
import io.github.alelk.tgvd.features.common.component.TrackPreferencesForm
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Editor screen for creating/editing a channel directory entry.
 * If [channelId] is null — create mode.
 * If [prefillChannelId] / [prefillExtractor] / [prefillChannelName] are provided — pre-fill from preview.
 */
class ChannelEditorScreen(
    private val channelId: String? = null,
    private val prefillChannelId: String? = null,
    private val prefillExtractor: String? = null,
    private val prefillChannelName: String? = null,
    private val onSaved: (() -> Unit)? = null,
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val client = koinInject<TgVideoDownloaderClient>()
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        var isLoading by remember { mutableStateOf(channelId != null) }
        var isSaving by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        // Form state
        var name by remember { mutableStateOf(prefillChannelName ?: "") }
        var platformChannelId by remember { mutableStateOf(prefillChannelId ?: "") }
        var extractor by remember { mutableStateOf(prefillExtractor ?: "youtube") }
        var tagsText by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        // Metadata overrides (simplified — just artist/series name override)
        var hasOverrides by remember { mutableStateOf(false) }
        var overrideCategory by remember { mutableStateOf(CategoryDto.MUSIC_VIDEO) }
        var artistOverride by remember { mutableStateOf("") }
        var seriesNameOverride by remember { mutableStateOf("") }

        // Audio/subtitle overrides — take priority over the matched rule and global settings
        var trackPreferences by remember { mutableStateOf(TrackPreferencesForm()) }

        // Load existing channel in edit mode
        LaunchedEffect(channelId) {
            if (channelId != null) {
                try {
                    client.getChannel(channelId).fold(
                        ifLeft = { errorMessage = it.message ?: "Failed to load channel" },
                        ifRight = { channel ->
                            name = channel.name
                            platformChannelId = channel.channelId
                            extractor = channel.extractor
                            tagsText = channel.tags.joinToString(", ")
                            notes = channel.notes ?: ""
                            trackPreferences = TrackPreferencesForm.from(channel.trackPreferences)
                            channel.metadataOverrides?.let { overrides ->
                                hasOverrides = true
                                when (overrides) {
                                    is MetadataTemplateDto.MusicVideo -> {
                                        overrideCategory = CategoryDto.MUSIC_VIDEO
                                        artistOverride = overrides.artistOverride ?: ""
                                    }
                                    is MetadataTemplateDto.SeriesEpisode -> {
                                        overrideCategory = CategoryDto.SERIES_EPISODE
                                        seriesNameOverride = overrides.seriesNameOverride ?: ""
                                    }
                                    is MetadataTemplateDto.Other -> {
                                        overrideCategory = CategoryDto.OTHER
                                    }
                                }
                            }
                        },
                    )
                } finally {
                    isLoading = false
                }
            }
        }

        val isCreate = channelId == null

        fun buildOverrides(): MetadataTemplateDto? {
            if (!hasOverrides) return null
            return when (overrideCategory) {
                CategoryDto.MUSIC_VIDEO -> MetadataTemplateDto.MusicVideo(
                    artistOverride = artistOverride.takeIf { it.isNotBlank() },
                )
                CategoryDto.SERIES_EPISODE -> MetadataTemplateDto.SeriesEpisode(
                    seriesNameOverride = seriesNameOverride.takeIf { it.isNotBlank() },
                )
                CategoryDto.OTHER -> MetadataTemplateDto.Other()
            }
        }

        fun parseTags(): List<String> = tagsText.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }

        fun save() {
            isSaving = true
            errorMessage = null
            scope.launch {
                try {
                    val saved = if (isCreate) {
                        client.createChannel(
                            CreateChannelDto(
                                channelId = platformChannelId,
                                extractor = extractor,
                                name = name,
                                tags = parseTags(),
                                metadataOverrides = buildOverrides(),
                                notes = notes.takeIf { it.isNotBlank() },
                                trackPreferences = trackPreferences.toDto(),
                            ),
                        )
                    } else {
                        client.updateChannel(
                            requireNotNull(channelId),
                            UpdateChannelDto(
                                name = name,
                                tags = parseTags(),
                                metadataOverrides = buildOverrides(),
                                notes = notes.takeIf { it.isNotBlank() },
                                trackPreferences = trackPreferences.toDto(),
                            ),
                        )
                    }
                    saved.fold(
                        ifLeft = { errorMessage = it.message ?: "Failed to save channel" },
                        ifRight = {
                            onSaved?.invoke()
                            navigator.pop()
                        },
                    )
                } finally {
                    isSaving = false
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isCreate) "Add Channel" else "Edit Channel") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(TgvdIcons.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Basic info
                SectionCard(title = "Channel Info", icon = TgvdIcons.Videocam) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = platformChannelId,
                        onValueChange = { platformChannelId = it },
                        label = { Text("Channel ID (platform)") },
                        singleLine = true,
                        enabled = isCreate, // Can't change channel ID after creation
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = extractor,
                        onValueChange = { extractor = it },
                        label = { Text("Platform (e.g. youtube, rutube)") },
                        singleLine = true,
                        enabled = isCreate, // Can't change extractor after creation
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tags
                SectionCard(title = "Tags", icon = TgvdIcons.Label) {
                    // Predefined quick-add tags
                    val predefinedTags = listOf("music-video", "series")
                    val currentTags = parseTags().toSet()

                    Text(
                        "Quick add",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        predefinedTags.forEach { tag ->
                            val isSelected = tag in currentTags
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val tags = parseTags().toMutableList()
                                    if (isSelected) tags.remove(tag) else tags.add(tag)
                                    tagsText = tags.joinToString(", ")
                                },
                                label = { Text(tag) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tagsText,
                        onValueChange = { tagsText = it },
                        label = { Text("Tags (comma-separated)") },
                        singleLine = true,
                        supportingText = { Text("e.g. music-video, pop, lofi") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Metadata overrides
                SectionCard(title = "Metadata Overrides", icon = TgvdIcons.Edit) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            checked = hasOverrides,
                            onCheckedChange = { hasOverrides = it },
                        )
                        Text("Override metadata for this channel")
                    }

                    if (hasOverrides) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Category selector
                        Text("Category", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val categories = CategoryDto.entries
                            categories.forEachIndexed { index, cat ->
                                SegmentedButton(
                                    selected = overrideCategory == cat,
                                    onClick = { overrideCategory = cat },
                                    shape = SegmentedButtonDefaults.itemShape(index, categories.size),
                                ) {
                                    Text(
                                        when (cat) {
                                            CategoryDto.MUSIC_VIDEO -> "Music"
                                            CategoryDto.SERIES_EPISODE -> "Series"
                                            CategoryDto.OTHER -> "Other"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        when (overrideCategory) {
                            CategoryDto.MUSIC_VIDEO -> {
                                OutlinedTextField(
                                    value = artistOverride,
                                    onValueChange = { artistOverride = it },
                                    label = { Text("Artist Override") },
                                    singleLine = true,
                                    supportingText = { Text("Will be used instead of parsing from video title") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            CategoryDto.SERIES_EPISODE -> {
                                OutlinedTextField(
                                    value = seriesNameOverride,
                                    onValueChange = { seriesNameOverride = it },
                                    label = { Text("Series Name Override") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            CategoryDto.OTHER -> {
                                Text(
                                    "No override fields for 'Other' category.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Audio & subtitle tracks
                SectionCard(title = "Audio & Subtitles", icon = TgvdIcons.Videocam) {
                    TrackPreferencesEditor(
                        form = trackPreferences,
                        onChange = { trackPreferences = it },
                        inheritFrom = "the matched rule or global settings",
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notes
                SectionCard(title = "Notes", icon = TgvdIcons.Label) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Error
                errorMessage?.let { error ->
                    ErrorCard(message = error)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Save button
                Button(
                    onClick = { save() },
                    enabled = !isSaving &&
                        name.isNotBlank() &&
                        platformChannelId.isNotBlank() &&
                        extractor.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (isSaving) {
                            "Saving..."
                        } else if (isCreate) {
                            "Add Channel"
                        } else {
                            "Save"
                        },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
