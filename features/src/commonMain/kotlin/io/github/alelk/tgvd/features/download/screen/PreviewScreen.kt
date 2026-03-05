package io.github.alelk.tgvd.features.download.screen

import androidx.compose.foundation.clickable
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
import io.github.alelk.tgvd.api.contract.storage.MediaContainerDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.api.contract.storage.VideoQualityDto
import io.github.alelk.tgvd.api.contract.channel.ChannelDto
import io.github.alelk.tgvd.features.channels.screen.ChannelEditorScreen
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

        // Channel directory: check if this channel is already registered
        var existingChannel by remember { mutableStateOf<ChannelDto?>(null) }
        var channelChecked by remember { mutableStateOf(false) }

        fun reloadChannel() {
            scope.launch {
                try {
                    val result = client.getChannels(
                        channelId = preview.videoInfo.channelId,
                        extractor = preview.videoInfo.extractor,
                    )
                    existingChannel = result.items.firstOrNull()
                } catch (_: Exception) {
                    // Ignore — non-critical
                } finally {
                    channelChecked = true
                }
            }
        }

        LaunchedEffect(Unit) { reloadChannel() }

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
        var originalMaxQuality by remember { mutableStateOf(preview.storagePlan.original.maxQuality) }
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
                            Text(stringResource(Res.string.preview_title))
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
                            Icon(TgvdIcons.ArrowBack, contentDescription = stringResource(Res.string.action_back))
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
                SectionCard(title = stringResource(Res.string.preview_video_info), icon = TgvdIcons.Videocam) {
                    InfoRow(stringResource(Res.string.label_title), preview.videoInfo.title)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoRow(stringResource(Res.string.label_channel), preview.videoInfo.channelName)
                        }
                        // Overflow menu for channel directory
                        if (channelChecked) {
                            val ch = existingChannel
                            if (ch != null) {
                                // Channel already in directory → three-dot menu
                                var menuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                                        Icon(TgvdIcons.MoreVert, contentDescription = "Channel options", modifier = Modifier.size(20.dp))
                                    }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.channels_edit_in_directory)) },
                                            onClick = {
                                                menuExpanded = false
                                                navigator.push(ChannelEditorScreen(
                                                    channelId = ch.id,
                                                    onSaved = { reloadChannel() },
                                                ))
                                            },
                                            leadingIcon = { Icon(TgvdIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    InfoRow(stringResource(Res.string.label_duration), formatDuration(preview.videoInfo.durationSeconds))
                    InfoRow(stringResource(Res.string.label_platform), preview.videoInfo.extractor)
                    preview.videoInfo.uploadDate?.let { InfoRow(stringResource(Res.string.label_upload_date), it) }

                    // "Add to Channel Directory" — only when NOT already in directory
                    if (channelChecked && existingChannel == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                navigator.push(
                                    ChannelEditorScreen(
                                        prefillChannelId = preview.videoInfo.channelId,
                                        prefillExtractor = preview.videoInfo.extractor,
                                        prefillChannelName = preview.videoInfo.channelName,
                                        onSaved = { reloadChannel() },
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(TgvdIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(Res.string.channels_add_to_directory), style = MaterialTheme.typography.labelMedium)
                        }
                    }
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

                // Storage Plan (collapsible, collapsed by default)
                var storagePlanExpanded by remember { mutableStateOf(false) }
                val noRule = preview.matchedRule == null

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (noRule)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    // Header row — always visible, tap to expand
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { storagePlanExpanded = !storagePlanExpanded }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                TgvdIcons.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    stringResource(Res.string.preview_storage_plan),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                if (!storagePlanExpanded) {
                                    // Compact summary when collapsed
                                    val qualitySuffix = originalMaxQuality?.let { " · ${qualityLabel(it)}" } ?: ""
                                    Text(
                                        text = "${originalFormat.serialized}$qualitySuffix",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (noRule) {
                                Text(
                                    stringResource(Res.string.preview_storage_no_rule),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            } else {
                                preview.matchedRule?.name?.let { ruleName ->
                                    Text(
                                        "(${stringResource(Res.string.preview_storage_from_rule)}: $ruleName)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                            }
                            Icon(
                                if (storagePlanExpanded) TgvdIcons.ExpandLess else TgvdIcons.ExpandMore,
                                contentDescription = if (storagePlanExpanded)
                                    stringResource(Res.string.rule_collapse)
                                else
                                    stringResource(Res.string.rule_expand),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Expanded content
                    if (storagePlanExpanded) {
                        HorizontalDivider()
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

                            // — Original output —
                            Text(
                                stringResource(Res.string.preview_original),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = originalPath,
                                onValueChange = { originalPath = it },
                                label = { Text(stringResource(Res.string.preview_storage_path)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false,
                                minLines = 2,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Container + Quality in one row
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val selectedContainer = (originalFormat as? OutputFormatDto.OriginalVideo)?.container
                                ContainerDropdown(
                                    selectedContainer = selectedContainer,
                                    onContainerSelected = { originalFormat = OutputFormatDto.OriginalVideo(it) },
                                    modifier = Modifier.weight(1f),
                                )
                                QualityDropdown(
                                    selectedQuality = originalMaxQuality,
                                    onQualitySelected = { originalMaxQuality = it },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            // — Additional outputs —
                            additionalOutputs.forEachIndexed { index, target ->
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                Text(
                                    "${stringResource(Res.string.preview_additional)} #${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = target.path,
                                    onValueChange = { newPath ->
                                        additionalOutputs[index] = target.copy(path = newPath)
                                    },
                                    label = { Text(stringResource(Res.string.preview_storage_path)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    minLines = 2,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                // Container + Quality in one row
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val addContainer = (target.format as? OutputFormatDto.OriginalVideo)?.container
                                    ContainerDropdown(
                                        selectedContainer = addContainer,
                                        onContainerSelected = { container ->
                                            additionalOutputs[index] = target.copy(
                                                format = OutputFormatDto.OriginalVideo(container),
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    QualityDropdown(
                                        selectedQuality = target.maxQuality,
                                        onQualitySelected = { q ->
                                            additionalOutputs[index] = target.copy(maxQuality = q)
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
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
                                        maxQuality = originalMaxQuality,
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
                    Text(if (isCreating) stringResource(Res.string.preview_creating) else stringResource(Res.string.preview_download_button))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun mutableStateSetOf(vararg elements: String): MutableSet<String> {
    return remember { mutableSetOf(*elements) }
}

/** Human-readable label for a VideoQualityDto value */
@Composable
private fun qualityLabel(quality: VideoQualityDto): String = when (quality) {
    VideoQualityDto.BEST -> stringResource(Res.string.preview_storage_quality_best)
    VideoQualityDto.HD_1080 -> stringResource(Res.string.preview_storage_quality_1080p)
    VideoQualityDto.HD_720 -> stringResource(Res.string.preview_storage_quality_720p)
    VideoQualityDto.SD_480 -> stringResource(Res.string.preview_storage_quality_480p)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContainerDropdown(
    selectedContainer: MediaContainerDto?,
    onContainerSelected: (MediaContainerDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = selectedContainer?.extension?.uppercase() ?: "auto"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.preview_storage_container)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MediaContainerDto.entries.forEach { container ->
                DropdownMenuItem(
                    text = { Text(container.extension.uppercase()) },
                    onClick = {
                        onContainerSelected(container)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    trailingIcon = {
                        if (container == selectedContainer) {
                            Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityDropdown(
    selectedQuality: VideoQualityDto?,
    onQualitySelected: (VideoQualityDto?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = selectedQuality?.let { qualityLabel(it) }
        ?: stringResource(Res.string.preview_storage_quality_best)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.preview_storage_max_quality)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // "Best available" = null
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.preview_storage_quality_best)) },
                onClick = {
                    onQualitySelected(null)
                    expanded = false
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                trailingIcon = {
                    if (selectedQuality == null) Text("✓", color = MaterialTheme.colorScheme.primary)
                },
            )
            VideoQualityDto.entries.forEach { quality ->
                DropdownMenuItem(
                    text = { Text(qualityLabel(quality)) },
                    onClick = {
                        onQualitySelected(quality)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    trailingIcon = {
                        if (quality == selectedQuality) Text("✓", color = MaterialTheme.colorScheme.primary)
                    },
                )
            }
        }
    }
}
