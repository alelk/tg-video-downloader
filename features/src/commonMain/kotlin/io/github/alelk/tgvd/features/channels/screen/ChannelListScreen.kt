package io.github.alelk.tgvd.features.channels.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.channel.ChannelDto
import io.github.alelk.tgvd.features.common.component.*
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class ChannelListScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val client = koinInject<TgVideoDownloaderClient>()
        val navigator = LocalNavigator.currentOrThrow

        var channels by remember { mutableStateOf<List<ChannelDto>>(emptyList()) }
        var allTags by remember { mutableStateOf<List<String>>(emptyList()) }
        var selectedTag by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var deleteConfirmId by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        fun loadChannels() {
            scope.launch {
                try {
                    isLoading = channels.isEmpty()
                    val response = client.getChannels(tag = selectedTag)
                    channels = response.items
                    errorMessage = null
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Failed to load channels"
                } finally {
                    isLoading = false
                }
            }
        }

        fun loadTags() {
            scope.launch {
                try {
                    val response = client.getChannelTags()
                    allTags = response.tags
                } catch (_: Exception) {
                    // Tags are optional, ignore errors
                }
            }
        }

        LaunchedEffect(Unit) {
            loadTags()
            loadChannels()
        }

        LaunchedEffect(selectedTag) {
            loadChannels()
        }

        // Delete confirmation dialog
        deleteConfirmId?.let { channelId ->
            AlertDialog(
                onDismissRequest = { deleteConfirmId = null },
                title = { Text(stringResource(Res.string.action_delete)) },
                text = { Text(stringResource(Res.string.channels_delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { client.deleteChannel(channelId) }
                            deleteConfirmId = null
                            loadChannels()
                            loadTags()
                        }
                    }) { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmId = null }) { Text(stringResource(Res.string.action_cancel)) }
                },
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.channels_title)) },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        navigator.push(ChannelEditorScreen(channelId = null, onSaved = {
                            loadChannels()
                            loadTags()
                        }))
                    },
                ) {
                    Icon(TgvdIcons.Add, contentDescription = stringResource(Res.string.channels_add))
                }
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            ) {
                // Tag filter chips
                if (allTags.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.channels_filter_by_tag),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = selectedTag == null,
                                onClick = { selectedTag = null },
                                label = { Text(stringResource(Res.string.channels_filter_all)) },
                            )
                        }
                        items(allTags) { tag ->
                            FilterChip(
                                selected = selectedTag == tag,
                                onClick = { selectedTag = if (selectedTag == tag) null else tag },
                                label = { Text(tag) },
                            )
                        }
                    }
                }

                errorMessage?.let {
                    ErrorCard(message = it, onRetry = { loadChannels() })
                    Spacer(modifier = Modifier.height(8.dp))
                }

                when {
                    isLoading && channels.isEmpty() -> LoadingContent()
                    channels.isEmpty() -> EmptyContent(
                        if (selectedTag != null) stringResource(Res.string.channels_empty_filtered, selectedTag!!)
                        else stringResource(Res.string.channels_empty)
                    )
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(channels, key = { it.id }) { channel ->
                                ChannelCard(
                                    channel = channel,
                                    onEdit = {
                                        navigator.push(ChannelEditorScreen(channelId = channel.id, onSaved = {
                                            loadChannels()
                                            loadTags()
                                        }))
                                    },
                                    onDelete = { deleteConfirmId = channel.id },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: ChannelDto, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${channel.extractor} • ${channel.channelId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Tags
            if (channel.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    channel.tags.forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(tag, style = MaterialTheme.typography.labelSmall)
                            },
                        )
                    }
                }
            }

            // Metadata overrides indicator
            channel.metadataOverrides?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "✦ ${stringResource(Res.string.channels_has_overrides)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Notes
            channel.notes?.let { notes ->
                if (notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }

            // Actions
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onEdit) {
                    Icon(TgvdIcons.Edit, contentDescription = stringResource(Res.string.action_edit), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(TgvdIcons.Delete, contentDescription = stringResource(Res.string.action_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

