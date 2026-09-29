package io.github.alelk.tgvd.features.download.screen

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto

/**
 * The preview destination in the Download tab's navigator. Its content is [PreviewEntry]; the state lives in
 * [PreviewScreenModel], created per screen instance (hence the unique key) and disposed when it is popped.
 */
class PreviewScreen(private val initialPreview: PreviewResponseDto) : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        PreviewEntry(initialPreview)
    }
}
