package io.github.alelk.tgvd.features.jobs.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import io.github.alelk.tgvd.features.generated.resources.Res
import io.github.alelk.tgvd.features.generated.resources.ic_list
import org.jetbrains.compose.resources.vectorResource

object JobsTab : Tab {
    override val options: TabOptions
        @Composable get() {
            val icon = rememberVectorPainter(vectorResource(Res.drawable.ic_list))
            return remember(icon) { TabOptions(index = 1u, title = "Jobs", icon = icon) }
        }

    @Composable
    override fun Content() {
        JobListScreen()
    }
}
