package io.github.alelk.tgvd.features.download.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.features.common.component.InfoRow
import io.github.alelk.tgvd.features.common.component.SectionCard
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.common.util.formatDuration
import io.github.alelk.tgvd.features.generated.resources.Res
import io.github.alelk.tgvd.features.generated.resources.channels_add_to_directory
import io.github.alelk.tgvd.features.generated.resources.channels_edit_in_directory
import io.github.alelk.tgvd.features.generated.resources.label_channel
import io.github.alelk.tgvd.features.generated.resources.label_duration
import io.github.alelk.tgvd.features.generated.resources.label_platform
import io.github.alelk.tgvd.features.generated.resources.label_title
import io.github.alelk.tgvd.features.generated.resources.label_upload_date
import io.github.alelk.tgvd.features.generated.resources.preview_max_available_quality
import io.github.alelk.tgvd.features.generated.resources.preview_video_info
import org.jetbrains.compose.resources.stringResource

/** Read-only video facts plus the channel-directory actions (edit when registered, add when not). */
@Composable
internal fun VideoInfoSection(
    state: PreviewUiState,
    onEditChannel: (channelId: String) -> Unit,
    onAddChannel: () -> Unit,
) {
    val video = state.video
    SectionCard(title = stringResource(Res.string.preview_video_info), icon = TgvdIcons.Videocam) {
        InfoRow(stringResource(Res.string.label_title), video.title)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                InfoRow(stringResource(Res.string.label_channel), video.channelName)
            }
            // Channel already in the directory → three-dot menu
            val channelId = state.directoryChannelId
            if (state.channelChecked && channelId != null) {
                ChannelOptionsMenu(onEdit = { onEditChannel(channelId) })
            }
        }
        DurationRow(video)
        InfoRow(stringResource(Res.string.label_platform), video.extractor)
        video.uploadDate?.let { InfoRow(stringResource(Res.string.label_upload_date), it) }

        // "Add to Channel Directory" — only when NOT already in directory
        if (state.channelChecked && state.directoryChannelId == null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onAddChannel, modifier = Modifier.fillMaxWidth()) {
                Icon(TgvdIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(Res.string.channels_add_to_directory),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ChannelOptionsMenu(onEdit: () -> Unit) {
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
                    onEdit()
                },
                leadingIcon = { Icon(TgvdIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

/** Duration row with the max-quality badge. */
@Composable
private fun DurationRow(video: PreviewVideo) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            InfoRow(stringResource(Res.string.label_duration), formatDuration(video.durationSeconds))
        }
        video.maxQualityLabel?.let { quality ->
            SuggestionChip(
                onClick = {},
                label = { Text(quality, style = MaterialTheme.typography.labelSmall) },
                icon = {
                    Icon(
                        TgvdIcons.Movie,
                        contentDescription = stringResource(Res.string.preview_max_available_quality),
                        modifier = Modifier.size(14.dp),
                    )
                },
                modifier = Modifier.height(28.dp),
            )
        }
    }
}
