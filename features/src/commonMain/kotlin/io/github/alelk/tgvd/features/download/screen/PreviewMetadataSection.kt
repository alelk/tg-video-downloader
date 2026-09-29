package io.github.alelk.tgvd.features.download.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.features.common.component.SectionCard
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.common.util.categoryLabel
import io.github.alelk.tgvd.features.download.model.PreviewEditorValues

private val metadataTypes = CategoryDto.entries.toList()

/** The editable metadata: category, the category's fields and tags. */
@Composable
internal fun MetadataSection(state: PreviewUiState, onEvent: (PreviewEvent) -> Unit) {
    val editor = state.editor
    val fieldErrors = state.fieldErrors
    val ruleName = state.matchedRule?.let { it.name ?: "" }
    SectionCard(
        title = if (ruleName != null) "Metadata (rule: $ruleName)" else "Metadata",
        icon = TgvdIcons.Label,
    ) {
        // Category / Metadata type selector (unified)
        Text("Category", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        CategorySelector(selected = editor.metadataType, onSelect = { onEvent(PreviewEvent.CategorySelected(it)) })

        Spacer(modifier = Modifier.height(8.dp))

        // Title (always visible)
        MetadataTextField(MetadataField.TITLE, "Title", editor, fieldErrors, onEvent)

        // Type-specific fields
        when (editor.metadataType) {
            CategoryDto.MUSIC_VIDEO -> {
                Spacer(modifier = Modifier.height(8.dp))
                MetadataTextField(MetadataField.ARTIST, "Artist", editor, fieldErrors, onEvent)
                Spacer(modifier = Modifier.height(8.dp))
                MetadataTextField(MetadataField.ALBUM, "Album (optional)", editor, fieldErrors, onEvent)
            }
            CategoryDto.SERIES_EPISODE -> {
                Spacer(modifier = Modifier.height(8.dp))
                MetadataTextField(MetadataField.SERIES_NAME, "Series Name", editor, fieldErrors, onEvent)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetadataTextField(
                        MetadataField.SEASON,
                        "Season",
                        editor,
                        fieldErrors,
                        onEvent,
                        Modifier.weight(1f),
                    )
                    MetadataTextField(
                        MetadataField.EPISODE,
                        "Episode",
                        editor,
                        fieldErrors,
                        onEvent,
                        Modifier.weight(1f),
                    )
                }
            }
            CategoryDto.OTHER -> Unit
        }

        Spacer(modifier = Modifier.height(8.dp))
        MetadataTextField(MetadataField.TAGS, "Tags (comma-separated)", editor, fieldErrors, onEvent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelector(selected: CategoryDto, onSelect: (CategoryDto) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        metadataTypes.forEachIndexed { index, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index, metadataTypes.size),
            ) {
                Text(categoryLabel(type), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** A single-line metadata field; shows the field's validation error under it, if any. */
@Composable
private fun MetadataTextField(
    field: MetadataField,
    label: String,
    editor: PreviewEditorValues,
    fieldErrors: Map<String, String>,
    onEvent: (PreviewEvent) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val error = fieldErrors[field.key]
    OutlinedTextField(
        value = editor.valueOf(field),
        onValueChange = { onEvent(PreviewEvent.MetadataChanged(field, it)) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        isError = error != null,
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
    )
}

private fun PreviewEditorValues.valueOf(field: MetadataField): String = when (field) {
    MetadataField.TITLE -> title
    MetadataField.ARTIST -> artist
    MetadataField.ALBUM -> album
    MetadataField.SERIES_NAME -> seriesName
    MetadataField.SEASON -> season
    MetadataField.EPISODE -> episode
    MetadataField.TAGS -> tags
}
