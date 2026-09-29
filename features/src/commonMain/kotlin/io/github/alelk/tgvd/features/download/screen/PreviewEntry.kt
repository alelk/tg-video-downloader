package io.github.alelk.tgvd.features.download.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.features.channels.screen.ChannelEditorScreen
import org.koin.core.parameter.parametersOf

/**
 * Entry of the preview screen: acquires [PreviewScreenModel], collects its state, turns effects into
 * navigation and hands the rest to [PreviewContent].
 */
@Composable
internal fun Screen.PreviewEntry(initialPreview: PreviewResponseDto) {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinScreenModel<PreviewScreenModel> { parametersOf(initialPreview) }
    val state by screenModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(screenModel) {
        // Every time the screen is shown again (e.g. back from the channel editor) the directory is re-checked.
        screenModel.onEvent(PreviewEvent.Opened)
        screenModel.effects.collect { effect ->
            when (effect) {
                PreviewEffect.JobCreated -> navigator.pop()
            }
        }
    }

    PreviewContent(
        state = state,
        onEvent = screenModel::onEvent,
        onBack = { navigator.pop() },
        onEditChannel = { channelId -> navigator.push(ChannelEditorScreen(channelId = channelId)) },
        onAddChannel = {
            navigator.push(
                ChannelEditorScreen(
                    prefillChannelId = state.video.channelId,
                    prefillExtractor = state.video.extractor,
                    prefillChannelName = state.video.channelName,
                ),
            )
        },
    )
}
