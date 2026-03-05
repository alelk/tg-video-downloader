package io.github.alelk.tgvd.features.jobs.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.generated.resources.Res
import io.github.alelk.tgvd.features.generated.resources.tab_jobs
import org.jetbrains.compose.resources.stringResource

object JobsTab : Tab {
    override val options: TabOptions
        @Composable get() {
            val icon = rememberVectorPainter(TgvdIcons.List)
            val title = stringResource(Res.string.tab_jobs)
            return remember(icon, title) { TabOptions(index = 1u, title = title, icon = icon) }
        }

    @Composable
    override fun Content() {
        JobListScreen()
    }
}
