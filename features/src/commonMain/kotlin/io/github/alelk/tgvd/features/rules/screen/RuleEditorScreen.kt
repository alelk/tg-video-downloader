package io.github.alelk.tgvd.features.rules.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import io.github.alelk.tgvd.api.contract.rule.CreateRuleRequestDto
import io.github.alelk.tgvd.api.contract.storage.*
import io.github.alelk.tgvd.features.common.component.ErrorCard
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.common.util.categoryLabel
import io.github.alelk.tgvd.features.rules.component.MatchConditionEditor
import io.github.alelk.tgvd.features.rules.model.MatchConditionState
import io.github.alelk.tgvd.features.rules.model.isValid
import io.github.alelk.tgvd.features.rules.model.toDto
import io.github.alelk.tgvd.features.rules.model.toState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class RuleEditorScreen(
    private val ruleId: String?,
    private val onSaved: () -> Unit,
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val client = koinInject<TgVideoDownloaderClient>()
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val isEdit = ruleId != null

        // === Basic form state ===
        var name by remember { mutableStateOf("") }
        var enabled by remember { mutableStateOf(true) }
        var priority by remember { mutableStateOf("0") }
        var category by remember { mutableStateOf(CategoryDto.OTHER) }

        // === Match condition state ===
        var matchState by remember { mutableStateOf<MatchConditionState>(MatchConditionState.Simple()) }

        // === Download policy ===
        var maxQuality by remember { mutableStateOf(VideoQualityDto.BEST) }
        var preferredContainer by remember { mutableStateOf<MediaContainerDto?>(null) }
        var downloadSubtitles by remember { mutableStateOf(false) }
        var subtitleLanguages by remember { mutableStateOf("") }

        // === Outputs ===
        val outputStates = remember { mutableStateListOf(OutputState()) }

        // === Metadata template ===
        var titleOverride by remember { mutableStateOf("") }
        var titlePattern by remember { mutableStateOf("") }
        var defaultTags by remember { mutableStateOf("") }
        var artistOverride by remember { mutableStateOf("") }
        var artistPattern by remember { mutableStateOf("") }
        var seriesNameOverride by remember { mutableStateOf("") }
        var seasonPattern by remember { mutableStateOf("") }
        var episodePattern by remember { mutableStateOf("") }

        // === Status ===
        var isLoadingRule by remember { mutableStateOf(isEdit) }
        var isSaving by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        // === Expandable ===
        var downloadPolicyExpanded by remember { mutableStateOf(false) }
        var metadataExpanded by remember { mutableStateOf(false) }

        // Load existing rule
        LaunchedEffect(ruleId) {
            if (ruleId != null) {
                try {
                    val rule = client.getRule(ruleId)
                    name = rule.name
                    enabled = rule.enabled
                    priority = rule.priority.toString()
                    category = rule.category
                    matchState = rule.match.toState()

                    maxQuality = rule.downloadPolicy.maxQuality
                    preferredContainer = rule.downloadPolicy.preferredContainer
                    downloadSubtitles = rule.downloadPolicy.downloadSubtitles
                    subtitleLanguages = rule.downloadPolicy.subtitleLanguages.joinToString(", ")

                    outputStates.clear()
                    rule.outputs.forEach { out ->
                        outputStates.add(
                            OutputState(
                                pathTemplate = out.pathTemplate,
                                format = out.format,
                                maxQuality = out.maxQuality,
                                embedThumbnail = out.embedThumbnail,
                                embedMetadata = out.embedMetadata,
                                embedSubtitles = out.embedSubtitles,
                                normalizeAudio = out.normalizeAudio,
                            )
                        )
                    }

                    titleOverride = rule.metadataTemplate.titleOverride ?: ""
                    titlePattern = rule.metadataTemplate.titlePattern ?: ""
                    defaultTags = rule.metadataTemplate.defaultTags.joinToString(", ")
                    when (val mt = rule.metadataTemplate) {
                        is MetadataTemplateDto.MusicVideo -> {
                            artistOverride = mt.artistOverride ?: ""
                            artistPattern = mt.artistPattern ?: ""
                        }
                        is MetadataTemplateDto.SeriesEpisode -> {
                            seriesNameOverride = mt.seriesNameOverride ?: ""
                            seasonPattern = mt.seasonPattern ?: ""
                            episodePattern = mt.episodePattern ?: ""
                        }
                        is MetadataTemplateDto.Other -> {}
                    }
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Failed to load rule"
                } finally {
                    isLoadingRule = false
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isEdit) "Edit Rule" else "Create Rule") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(TgvdIcons.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            if (isLoadingRule) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ========== BASIC SETTINGS ==========
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Enabled")
                    }
                    OutlinedTextField(
                        value = priority,
                        onValueChange = { priority = it.filter { c -> c.isDigit() || c == '-' } },
                        label = { Text("Priority") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryDto.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(categoryLabel(cat)) },
                        )
                    }
                }

                HorizontalDivider()

                // ========== MATCH CONDITIONS ==========
                Text("Match Conditions", style = MaterialTheme.typography.titleSmall)
                MatchConditionEditor(state = matchState, onChanged = { matchState = it })

                HorizontalDivider()

                // ========== DOWNLOAD POLICY ==========
                SectionHeader("Download Settings", downloadPolicyExpanded) {
                    downloadPolicyExpanded = !downloadPolicyExpanded
                }
                if (downloadPolicyExpanded) {
                    DownloadPolicySection(
                        maxQuality, { maxQuality = it },
                        preferredContainer, { preferredContainer = it },
                        downloadSubtitles, { downloadSubtitles = it },
                        subtitleLanguages, { subtitleLanguages = it },
                    )
                }

                HorizontalDivider()

                // ========== OUTPUTS ==========
                Text("Outputs", style = MaterialTheme.typography.titleSmall)
                Text(
                    "First output = original file. Additional = conversions/copies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutputsEditor(outputStates)

                HorizontalDivider()

                // ========== METADATA TEMPLATE ==========
                SectionHeader("Metadata Template", metadataExpanded) {
                    metadataExpanded = !metadataExpanded
                }
                if (metadataExpanded) {
                    MetadataTemplateSection(
                        category, titleOverride, { titleOverride = it },
                        titlePattern, { titlePattern = it }, defaultTags, { defaultTags = it },
                        artistOverride, { artistOverride = it }, artistPattern, { artistPattern = it },
                        seriesNameOverride, { seriesNameOverride = it },
                        seasonPattern, { seasonPattern = it }, episodePattern, { episodePattern = it },
                    )
                }

                // Error
                errorMessage?.let { ErrorCard(message = it) }

                // Save
                Button(
                    onClick = {
                        if (name.isBlank()) { errorMessage = "Name is required"; return@Button }
                        if (!matchState.isValid()) { errorMessage = "All match conditions must have values"; return@Button }
                        if (outputStates.isEmpty()) { errorMessage = "At least one output is required"; return@Button }
                        if (outputStates.any { it.pathTemplate.isBlank() }) { errorMessage = "All outputs must have a path template"; return@Button }
                        isSaving = true; errorMessage = null
                        scope.launch {
                            try {
                                val request = CreateRuleRequestDto(
                                    name = name,
                                    enabled = enabled,
                                    priority = priority.toIntOrNull() ?: 0,
                                    match = matchState.toDto(),
                                    category = category,
                                    metadataTemplate = buildMetadataTemplate(
                                        category, titleOverride, titlePattern, defaultTags,
                                        artistOverride, artistPattern, seriesNameOverride, seasonPattern, episodePattern,
                                    ),
                                    downloadPolicy = DownloadPolicyDto(
                                        maxQuality = maxQuality,
                                        preferredContainer = preferredContainer,
                                        downloadSubtitles = downloadSubtitles,
                                        subtitleLanguages = subtitleLanguages.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                    ),
                                    outputs = outputStates.map { it.toDto() },
                                )
                                if (ruleId != null) client.updateRule(ruleId, request)
                                else client.createRule(request)
                                onSaved()
                                navigator.pop()
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Failed to save rule"
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isSaving) "Saving..." else "Save")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ==================== Output State ====================

private data class OutputState(
    var pathTemplate: String = "",
    var format: OutputFormatDto = OutputFormatDto.OriginalVideo(MediaContainerDto.WEBM),
    var maxQuality: VideoQualityDto? = null,
    var embedThumbnail: Boolean = false,
    var embedMetadata: Boolean = false,
    var embedSubtitles: Boolean = false,
    var normalizeAudio: Boolean = false,
) {
    fun toDto() = OutputRuleDto(
        pathTemplate = pathTemplate,
        format = format,
        maxQuality = maxQuality,
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedSubtitles = embedSubtitles,
        normalizeAudio = normalizeAudio,
    )
}

// ==================== Output Editor ====================

@Composable
private fun OutputsEditor(outputs: SnapshotStateList<OutputState>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        outputs.forEachIndexed { index, output ->
            var expanded by remember { mutableStateOf(true) }
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), Arrangement.spacedBy(8.dp)) {
                    // Header
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(
                            if (index == 0) "Original" else "Output #${index + 1}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row {
                            TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "▲" else "▼")
                            }
                            if (outputs.size > 1) {
                                IconButton(onClick = { outputs.removeAt(index) }, Modifier.size(32.dp)) {
                                    Icon(TgvdIcons.Delete, "Remove", Modifier.size(18.dp), MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    if (expanded) {
                        // Path template
                        OutlinedTextField(
                            value = output.pathTemplate,
                            onValueChange = { outputs[index] = output.copy(pathTemplate = it) },
                            label = { Text("Path Template") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = output.pathTemplate.isBlank(),
                        )
                        Text(
                            "{channelName}, {title}, {artist}, {seriesName}, {videoId}, {year}, {date}, {ext}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // Format selector
                        Text("Format", style = MaterialTheme.typography.labelMedium)
                        FormatSelector(output.format) { outputs[index] = output.copy(format = it) }

                        // Max quality (optional)
                        Text("Max Quality", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = output.maxQuality == null,
                                onClick = { outputs[index] = output.copy(maxQuality = null) },
                                label = { Text("Original", style = MaterialTheme.typography.bodySmall) },
                            )
                            VideoQualityDto.entries.forEach { q ->
                                FilterChip(
                                    selected = output.maxQuality == q,
                                    onClick = { outputs[index] = output.copy(maxQuality = q) },
                                    label = { Text(qualityLabel(q), style = MaterialTheme.typography.bodySmall) },
                                )
                            }
                        }

                        // Post-processing toggles
                        Text("Post-Processing", style = MaterialTheme.typography.labelMedium)
                        ToggleRow("Embed Thumbnail", output.embedThumbnail) { outputs[index] = output.copy(embedThumbnail = it) }
                        ToggleRow("Embed Metadata", output.embedMetadata) { outputs[index] = output.copy(embedMetadata = it) }
                        ToggleRow("Embed Subtitles", output.embedSubtitles) { outputs[index] = output.copy(embedSubtitles = it) }
                        ToggleRow("Normalize Audio", output.normalizeAudio) { outputs[index] = output.copy(normalizeAudio = it) }
                    }
                }
            }
        }

        // Add output button
        OutlinedButton(
            onClick = {
                outputs.add(OutputState(
                    format = OutputFormatDto.ConvertedVideo(MediaContainerDto.MP4),
                    embedThumbnail = true,
                    embedMetadata = true,
                ))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(TgvdIcons.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add Output")
        }
    }
}

@Composable
private fun FormatSelector(current: OutputFormatDto, onChange: (OutputFormatDto) -> Unit) {
    val formatGroups = listOf(
        "Original" to MediaContainerDto.entries.map { OutputFormatDto.OriginalVideo(it) },
        "Convert" to MediaContainerDto.entries.map { OutputFormatDto.ConvertedVideo(it) },
        "Audio" to AudioFormatDto.entries.map { OutputFormatDto.Audio(it) },
        "Thumbnail" to ImageFormatDto.entries.map { OutputFormatDto.Thumbnail(it) },
    )

    // Group selector
    val currentGroup = when (current) {
        is OutputFormatDto.OriginalVideo -> "Original"
        is OutputFormatDto.ConvertedVideo -> "Convert"
        is OutputFormatDto.Audio -> "Audio"
        is OutputFormatDto.Thumbnail -> "Thumbnail"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        formatGroups.forEach { (groupName, formats) ->
            FilterChip(
                selected = currentGroup == groupName,
                onClick = { onChange(formats.first()) },
                label = { Text(groupName, style = MaterialTheme.typography.bodySmall) },
            )
        }
    }

    // Extension selector within group
    val currentFormats = formatGroups.find { it.first == currentGroup }?.second.orEmpty()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        currentFormats.forEach { fmt ->
            FilterChip(
                selected = current.extension == fmt.extension,
                onClick = { onChange(fmt) },
                label = { Text(fmt.extension.uppercase(), style = MaterialTheme.typography.bodySmall) },
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ==================== Other Sections ====================

@Composable
private fun SectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onToggle) { Text(if (expanded) "Collapse" else "Expand") }
    }
}

@Composable
private fun DownloadPolicySection(
    maxQuality: VideoQualityDto, onMaxQualityChange: (VideoQualityDto) -> Unit,
    preferredContainer: MediaContainerDto?, onContainerChange: (MediaContainerDto?) -> Unit,
    downloadSubtitles: Boolean, onSubtitlesChange: (Boolean) -> Unit,
    subtitleLanguages: String, onLanguagesChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp), Arrangement.spacedBy(8.dp)) {
        Text("Max Download Quality", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VideoQualityDto.entries.forEach { q ->
                FilterChip(selected = maxQuality == q, onClick = { onMaxQualityChange(q) },
                    label = { Text(qualityLabel(q), style = MaterialTheme.typography.bodySmall) })
            }
        }
        Text("Preferred Container", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = preferredContainer == null, onClick = { onContainerChange(null) },
                label = { Text("Auto", style = MaterialTheme.typography.bodySmall) })
            MediaContainerDto.entries.forEach { c ->
                FilterChip(selected = preferredContainer == c, onClick = { onContainerChange(c) },
                    label = { Text(c.extension.uppercase(), style = MaterialTheme.typography.bodySmall) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = downloadSubtitles, onCheckedChange = onSubtitlesChange)
            Spacer(Modifier.width(8.dp)); Text("Download Subtitles")
        }
        if (downloadSubtitles) {
            OutlinedTextField(subtitleLanguages, onLanguagesChange, label = { Text("Languages") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Comma-separated (en, ru, de)", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetadataTemplateSection(
    category: CategoryDto,
    titleOverride: String, onTitleOverride: (String) -> Unit,
    titlePattern: String, onTitlePattern: (String) -> Unit,
    defaultTags: String, onDefaultTags: (String) -> Unit,
    artistOverride: String, onArtistOverride: (String) -> Unit,
    artistPattern: String, onArtistPattern: (String) -> Unit,
    seriesNameOverride: String, onSeriesOverride: (String) -> Unit,
    seasonPattern: String, onSeasonPattern: (String) -> Unit,
    episodePattern: String, onEpisodePattern: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp), Arrangement.spacedBy(8.dp)) {
        when (category) {
            CategoryDto.MUSIC_VIDEO -> {
                OutlinedTextField(artistOverride, onArtistOverride, label = { Text("Artist Override") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(artistPattern, onArtistPattern, label = { Text("Artist Pattern (regex)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            CategoryDto.SERIES_EPISODE -> {
                OutlinedTextField(seriesNameOverride, onSeriesOverride, label = { Text("Series Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(seasonPattern, onSeasonPattern, label = { Text("Season Pattern") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(episodePattern, onEpisodePattern, label = { Text("Episode Pattern") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            CategoryDto.OTHER -> {}
        }
        OutlinedTextField(titleOverride, onTitleOverride, label = { Text("Title Override") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(titlePattern, onTitlePattern, label = { Text("Title Pattern (regex)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(defaultTags, onDefaultTags, label = { Text("Default Tags (comma-separated)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}

// ==================== Helpers ====================

private fun qualityLabel(q: VideoQualityDto) = when (q) {
    VideoQualityDto.BEST -> "Best"
    VideoQualityDto.HD_1080 -> "1080p"
    VideoQualityDto.HD_720 -> "720p"
    VideoQualityDto.SD_480 -> "480p"
}

private fun buildMetadataTemplate(
    category: CategoryDto, titleOverride: String, titlePattern: String, defaultTags: String,
    artistOverride: String, artistPattern: String,
    seriesNameOverride: String, seasonPattern: String, episodePattern: String,
): MetadataTemplateDto {
    val tags = defaultTags.split(",").map { it.trim() }.filter { it.isNotBlank() }
    return when (category) {
        CategoryDto.MUSIC_VIDEO -> MetadataTemplateDto.MusicVideo(
            artistOverride = artistOverride.ifBlank { null }, artistPattern = artistPattern.ifBlank { null },
            titleOverride = titleOverride.ifBlank { null }, titlePattern = titlePattern.ifBlank { null }, defaultTags = tags,
        )
        CategoryDto.SERIES_EPISODE -> MetadataTemplateDto.SeriesEpisode(
            seriesNameOverride = seriesNameOverride.ifBlank { null }, seasonPattern = seasonPattern.ifBlank { null },
            episodePattern = episodePattern.ifBlank { null },
            titleOverride = titleOverride.ifBlank { null }, titlePattern = titlePattern.ifBlank { null }, defaultTags = tags,
        )
        CategoryDto.OTHER -> MetadataTemplateDto.Other(
            titleOverride = titleOverride.ifBlank { null }, titlePattern = titlePattern.ifBlank { null }, defaultTags = tags,
        )
    }
}
