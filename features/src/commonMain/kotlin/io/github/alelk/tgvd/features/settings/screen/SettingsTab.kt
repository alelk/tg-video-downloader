package io.github.alelk.tgvd.features.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import io.github.alelk.tgvd.features.common.icon.TgvdIcons

object SettingsTab : Tab {
    override val options: TabOptions
        @Composable get() {
            val icon = rememberVectorPainter(TgvdIcons.Settings)
            return remember(icon) { TabOptions(index = 4u, title = "Settings", icon = icon) }
        }

    @Composable
    override fun Content() {
        SettingsScreen()
    }
}
