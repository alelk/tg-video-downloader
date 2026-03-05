package io.github.alelk.tgvd.features.common.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.features.common.state.LocaleState
import io.github.alelk.tgvd.features.common.state.WorkspaceState

/**
 * Top app bar showing the current workspace as a clickable chip.
 * Clicking the chip opens a bottom sheet with workspace list and "Create new" option.
 * Right side: locale switcher button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceTopBar(
    workspaceState: WorkspaceState,
    localeState: LocaleState,
    onCreateWorkspace: () -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    var showLocalePicker by remember { mutableStateOf(false) }
    val selected = workspaceState.selectedWorkspace

    CenterAlignedTopAppBar(
        title = {
            AssistChip(
                onClick = { showSheet = true },
                label = {
                    Text(
                        text = selected?.name ?: "Workspace",
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        },
        actions = {
            // Locale switcher
            Box {
                FilterChip(
                    selected = false,
                    onClick = { showLocalePicker = true },
                    label = {
                        Text(
                            text = LocaleState.flagFor(localeState.currentLocale),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    modifier = Modifier.padding(end = 8.dp),
                )
                DropdownMenu(
                    expanded = showLocalePicker,
                    onDismissRequest = { showLocalePicker = false },
                ) {
                    LocaleState.SUPPORTED_LOCALES.forEach { locale ->
                        DropdownMenuItem(
                            text = {
                                Text("${LocaleState.flagFor(locale)}  ${LocaleState.labelFor(locale)}")
                            },
                            onClick = {
                                localeState.setLocale(locale)
                                showLocalePicker = false
                            },
                            trailingIcon = {
                                if (locale == localeState.currentLocale) {
                                    Text("✓", color = MaterialTheme.colorScheme.primary)
                                }
                            },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Workspaces",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                workspaceState.workspaces.forEach { ws ->
                    ListItem(
                        headlineContent = { Text(ws.name) },
                        supportingContent = { Text(ws.slug, style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            if (ws.slug == selected?.slug) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.clickable {
                            workspaceState.selectWorkspace(ws)
                            showSheet = false
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ListItem(
                    headlineContent = { Text("Create new workspace") },
                    leadingContent = {
                        Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        showSheet = false
                        onCreateWorkspace()
                    },
                )
            }
        }
    }
}
