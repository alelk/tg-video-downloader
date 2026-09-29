package io.github.alelk.tgvd.features.download.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.api.contract.storage.MediaContainerDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.VideoQualityDto
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.download.model.PreviewEditorValues
import io.github.alelk.tgvd.features.generated.resources.Res
import io.github.alelk.tgvd.features.generated.resources.preview_additional
import io.github.alelk.tgvd.features.generated.resources.preview_original
import io.github.alelk.tgvd.features.generated.resources.preview_storage_from_rule
import io.github.alelk.tgvd.features.generated.resources.preview_storage_no_rule
import io.github.alelk.tgvd.features.generated.resources.preview_storage_path
import io.github.alelk.tgvd.features.generated.resources.preview_storage_plan
import io.github.alelk.tgvd.features.generated.resources.rule_collapse
import io.github.alelk.tgvd.features.generated.resources.rule_expand
import org.jetbrains.compose.resources.stringResource

/** Storage plan: collapsed to a one-line summary by default; expanded, the original and additional outputs. */
@Composable
internal fun StoragePlanSection(
    editor: PreviewEditorValues,
    matchedRuleName: String?,
    noRule: Boolean,
    onEvent: (PreviewEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (noRule) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
        ),
    ) {
        StoragePlanHeader(
            editor = editor,
            matchedRuleName = matchedRuleName,
            noRule = noRule,
            expanded = expanded,
            onToggle = { expanded = !expanded },
        )
        if (expanded) {
            HorizontalDivider()
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                OutputTargetEditor(
                    title = stringResource(Res.string.preview_original),
                    path = editor.originalPath,
                    format = editor.originalFormat,
                    maxQuality = editor.originalMaxQuality,
                    onPathChange = { onEvent(PreviewEvent.OriginalPathChanged(it)) },
                    onContainerSelected = { onEvent(PreviewEvent.OriginalContainerSelected(it)) },
                    onQualitySelected = { onEvent(PreviewEvent.OriginalQualitySelected(it)) },
                )
                editor.additionalOutputs.forEachIndexed { index, target ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    OutputTargetEditor(
                        title = "${stringResource(Res.string.preview_additional)} #${index + 1}",
                        path = target.path,
                        format = target.format,
                        maxQuality = target.maxQuality,
                        onPathChange = { onEvent(PreviewEvent.AdditionalPathChanged(index, it)) },
                        onContainerSelected = { onEvent(PreviewEvent.AdditionalContainerSelected(index, it)) },
                        onQualitySelected = { onEvent(PreviewEvent.AdditionalQualitySelected(index, it)) },
                    )
                }
            }
        }
    }
}

/** Header row — always visible, tap to expand. */
@Composable
private fun StoragePlanHeader(
    editor: PreviewEditorValues,
    matchedRuleName: String?,
    noRule: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
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
                Text(stringResource(Res.string.preview_storage_plan), style = MaterialTheme.typography.titleSmall)
                if (!expanded) {
                    // Compact summary when collapsed
                    val qualitySuffix = editor.originalMaxQuality?.let { " · ${qualityLabel(it)}" } ?: ""
                    Text(
                        text = "${editor.originalFormat.serialized}$qualitySuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RuleBadge(matchedRuleName = matchedRuleName, noRule = noRule)
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
    }
}

@Composable
private fun RuleBadge(matchedRuleName: String?, noRule: Boolean) {
    if (noRule) {
        Text(
            stringResource(Res.string.preview_storage_no_rule),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 8.dp),
        )
    } else {
        matchedRuleName?.let { ruleName ->
            Text(
                "(${stringResource(Res.string.preview_storage_from_rule)}: $ruleName)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/** One output: its title, path, and container + max quality in one row. */
@Composable
private fun OutputTargetEditor(
    title: String,
    path: String,
    format: OutputFormatDto,
    maxQuality: VideoQualityDto?,
    onPathChange: (String) -> Unit,
    onContainerSelected: (MediaContainerDto) -> Unit,
    onQualitySelected: (VideoQualityDto?) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(4.dp))
    OutlinedTextField(
        value = path,
        onValueChange = onPathChange,
        label = { Text(stringResource(Res.string.preview_storage_path)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
        minLines = 2,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ContainerDropdown(
            selectedContainer = (format as? OutputFormatDto.OriginalVideo)?.container,
            onContainerSelected = onContainerSelected,
            modifier = Modifier.weight(1f),
        )
        QualityDropdown(
            selectedQuality = maxQuality,
            onQualitySelected = onQualitySelected,
            modifier = Modifier.weight(1f),
        )
    }
}
