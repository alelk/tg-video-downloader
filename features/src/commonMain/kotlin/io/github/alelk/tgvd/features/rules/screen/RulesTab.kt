package io.github.alelk.tgvd.features.rules.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import io.github.alelk.tgvd.features.common.icon.TgvdIcons

object RulesTab : Tab {
    override val options: TabOptions
        @Composable get() {
            val icon = rememberVectorPainter(TgvdIcons.Rule)
            return remember(icon) { TabOptions(index = 2u, title = "Rules", icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(RuleListScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}
