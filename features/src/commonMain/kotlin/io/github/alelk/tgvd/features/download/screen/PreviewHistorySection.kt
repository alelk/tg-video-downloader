package io.github.alelk.tgvd.features.download.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.api.contract.storage.VideoQualityDto
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.generated.resources.Res
import io.github.alelk.tgvd.features.generated.resources.preview_download_cancelled
import io.github.alelk.tgvd.features.generated.resources.preview_download_failed
import io.github.alelk.tgvd.features.generated.resources.preview_download_history
import io.github.alelk.tgvd.features.generated.resources.preview_download_quality_best
import io.github.alelk.tgvd.features.generated.resources.preview_downloaded
import io.github.alelk.tgvd.features.generated.resources.rule_collapse
import io.github.alelk.tgvd.features.generated.resources.rule_expand
import org.jetbrains.compose.resources.stringResource

/** Earlier downloads of the same URL; expanded from the start when at least one of them completed. */
@Composable
internal fun DownloadHistoryCard(entries: List<DownloadHistoryItem>) {
    var expanded by remember { mutableStateOf(entries.any { it.status == DownloadHistoryStatus.COMPLETED }) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    TgvdIcons.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        stringResource(Res.string.preview_download_history),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (!expanded) {
                        Text(
                            historySummary(entries),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Icon(
                if (expanded) TgvdIcons.ExpandLess else TgvdIcons.ExpandMore,
                contentDescription = if (expanded) {
                    stringResource(Res.string.rule_collapse)
                } else {
                    stringResource(Res.string.rule_expand)
                },
                modifier = Modifier.size(20.dp),
            )
        }

        if (expanded) {
            HorizontalDivider()
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                entries.forEach { entry ->
                    DownloadHistoryRow(entry = entry)
                }
            }
        }
    }
}

/** Collapsed summary: the latest completed download (+ how many more), or the number of attempts. */
@Composable
private fun historySummary(entries: List<DownloadHistoryItem>): String {
    val completed = entries.filter { it.status == DownloadHistoryStatus.COMPLETED }
    val latest = completed.firstOrNull() ?: return "${entries.size} attempt(s)"
    val date = latest.finishedDate ?: "?"
    val quality = historyQualityLabel(latest.maxQuality)
    val format = latest.formatSummary.uppercase()
    return if (completed.size > 1) {
        "$date · $quality · $format (+${completed.size - 1})"
    } else {
        "$date · $quality · $format"
    }
}

@Composable
private fun DownloadHistoryRow(entry: DownloadHistoryItem) {
    val (icon, label, tint) = when (entry.status) {
        DownloadHistoryStatus.COMPLETED -> Triple(
            TgvdIcons.CheckCircle,
            stringResource(Res.string.preview_downloaded),
            MaterialTheme.colorScheme.primary,
        )
        DownloadHistoryStatus.FAILED -> Triple(
            TgvdIcons.ErrorIcon,
            stringResource(Res.string.preview_download_failed),
            MaterialTheme.colorScheme.error,
        )
        DownloadHistoryStatus.OTHER -> Triple(
            TgvdIcons.ErrorIcon,
            stringResource(Res.string.preview_download_cancelled),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "$label: ${entry.finishedDate ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = tint,
            )
            if (entry.status == DownloadHistoryStatus.COMPLETED) {
                Text(
                    "${historyQualityLabel(entry.maxQuality)} · ${entry.formatSummary.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Short label for a quality cap in the download history; `null` — best available. */
@Composable
private fun historyQualityLabel(quality: VideoQualityDto?): String = when (quality) {
    null, VideoQualityDto.BEST -> stringResource(Res.string.preview_download_quality_best)
    VideoQualityDto.HD_1080 -> "1080p"
    VideoQualityDto.HD_720 -> "720p"
    VideoQualityDto.SD_480 -> "480p"
}
