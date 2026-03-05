package io.github.alelk.tgvd.features.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.workspace.CreateWorkspaceRequestDto
import io.github.alelk.tgvd.features.channels.screen.ChannelsTab
import io.github.alelk.tgvd.features.common.component.CreateWorkspaceDialog
import io.github.alelk.tgvd.features.common.component.WorkspaceTopBar
import io.github.alelk.tgvd.features.common.state.LocaleState
import io.github.alelk.tgvd.features.common.state.WorkspaceState
import io.github.alelk.tgvd.features.download.screen.DownloadTab
import io.github.alelk.tgvd.features.jobs.screen.JobsTab
import io.github.alelk.tgvd.features.rules.screen.RulesTab
import io.github.alelk.tgvd.features.settings.screen.SettingsTab
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun AppNavigation() {
    val workspaceState = koinInject<WorkspaceState>()
    val localeState = koinInject<LocaleState>()
    val client = koinInject<TgVideoDownloaderClient>()
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    TabNavigator(DownloadTab) {
        Scaffold(
            topBar = {
                WorkspaceTopBar(
                    workspaceState = workspaceState,
                    localeState = localeState,
                    onCreateWorkspace = {
                        createError = null
                        showCreateDialog = true
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    TabItem(DownloadTab)
                    TabItem(JobsTab)
                    TabItem(RulesTab)
                    TabItem(ChannelsTab)
                    TabItem(SettingsTab)
                }
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
            ) {
                CurrentTab()
            }
        }
    }

    if (showCreateDialog) {
        CreateWorkspaceDialog(
            onDismiss = {
                showCreateDialog = false
                createError = null
            },
            onCreate = { slug, name ->
                isCreating = true
                createError = null
                scope.launch {
                    try {
                        val created = client.createWorkspace(
                            CreateWorkspaceRequestDto(slug = slug, name = name)
                        )
                        workspaceState.workspaces = workspaceState.workspaces + created
                        workspaceState.selectWorkspace(created)
                        showCreateDialog = false
                    } catch (e: Exception) {
                        createError = e.message ?: "Failed to create workspace"
                    } finally {
                        isCreating = false
                    }
                }
            },
            isCreating = isCreating,
            errorMessage = createError,
        )
    }
}

@Composable
private fun RowScope.TabItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current
    val options = tab.options
    NavigationBarItem(
        selected = tabNavigator.current == tab,
        onClick = { tabNavigator.current = tab },
        icon = {
            options.icon?.let { painter ->
                Icon(painter = painter, contentDescription = options.title)
            }
        },
        label = { Text(options.title) },
    )
}
