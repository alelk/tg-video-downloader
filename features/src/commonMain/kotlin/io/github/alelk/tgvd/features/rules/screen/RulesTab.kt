package io.github.alelk.tgvd.features.rules.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.generated.resources.Res
import io.github.alelk.tgvd.features.generated.resources.tab_rules
import org.jetbrains.compose.resources.stringResource

object RulesTab : Tab {
    override val options: TabOptions
        @Composable get() {
            val icon = rememberVectorPainter(TgvdIcons.Rule)
            val title = stringResource(Res.string.tab_rules)
            return remember(icon, title) { TabOptions(index = 2u, title = title, icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(RuleListScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}
