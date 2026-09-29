package io.github.alelk.tgvd.features.download.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.alelk.tgvd.api.contract.storage.MediaContainerDto
import io.github.alelk.tgvd.api.contract.storage.VideoQualityDto
import io.github.alelk.tgvd.features.generated.resources.Res
import io.github.alelk.tgvd.features.generated.resources.preview_storage_container
import io.github.alelk.tgvd.features.generated.resources.preview_storage_max_quality
import io.github.alelk.tgvd.features.generated.resources.preview_storage_quality_1080p
import io.github.alelk.tgvd.features.generated.resources.preview_storage_quality_480p
import io.github.alelk.tgvd.features.generated.resources.preview_storage_quality_720p
import io.github.alelk.tgvd.features.generated.resources.preview_storage_quality_best
import org.jetbrains.compose.resources.stringResource

/** Human-readable label for a VideoQualityDto value */
@Composable
internal fun qualityLabel(quality: VideoQualityDto): String = when (quality) {
    VideoQualityDto.BEST -> stringResource(Res.string.preview_storage_quality_best)
    VideoQualityDto.HD_1080 -> stringResource(Res.string.preview_storage_quality_1080p)
    VideoQualityDto.HD_720 -> stringResource(Res.string.preview_storage_quality_720p)
    VideoQualityDto.SD_480 -> stringResource(Res.string.preview_storage_quality_480p)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContainerDropdown(
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
internal fun QualityDropdown(
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
