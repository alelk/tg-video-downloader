package io.github.alelk.tgvd.features.download.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import io.github.alelk.tgvd.features.common.icon.TgvdIcons

object DownloadTab : Tab {
    override val options: TabOptions
        @Composable get() {
            val icon = rememberVectorPainter(TgvdIcons.Download)
            return remember(icon) { TabOptions(index = 0u, title = "Download", icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(UrlInputScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}
