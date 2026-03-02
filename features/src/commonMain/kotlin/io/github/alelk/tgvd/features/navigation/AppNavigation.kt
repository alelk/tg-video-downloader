package io.github.alelk.tgvd.features.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import io.github.alelk.tgvd.features.download.screen.DownloadTab
import io.github.alelk.tgvd.features.jobs.screen.JobsTab
import io.github.alelk.tgvd.features.rules.screen.RulesTab
import io.github.alelk.tgvd.features.settings.screen.SettingsTab

private val tabEmojis = mapOf(
    DownloadTab to "📥",
    JobsTab to "📋",
    RulesTab to "📐",
    SettingsTab to "⚙️",
)

@Composable
fun AppNavigation() {
    TabNavigator(DownloadTab) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    TabItem(DownloadTab)
                    TabItem(JobsTab)
                    TabItem(RulesTab)
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
}

@Composable
private fun RowScope.TabItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current
    val emoji = tabEmojis[tab] ?: ""
    NavigationBarItem(
        selected = tabNavigator.current == tab,
        onClick = { tabNavigator.current = tab },
        icon = { Text(emoji) },
        label = { Text(tab.options.title) },
    )
}
