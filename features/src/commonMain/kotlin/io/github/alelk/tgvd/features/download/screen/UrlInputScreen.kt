package io.github.alelk.tgvd.features.download.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.preview.PreviewRequestDto
import io.github.alelk.tgvd.features.common.component.ErrorCard
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.common.theme.LocalPlatformCallbacks
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class UrlInputScreen : Screen {

    @Composable
    override fun Content() {
        val client = koinInject<TgVideoDownloaderClient>()
        val navigator = LocalNavigator.currentOrThrow
        val platformCallbacks = LocalPlatformCallbacks.current

        var url by remember(platformCallbacks.prefilledUrl) {
            mutableStateOf(platformCallbacks.prefilledUrl.orEmpty())
        }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        val readClipboard = platformCallbacks.readTextFromClipboard

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Video Downloader",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    errorMessage = null
                },
                label = { Text("Video URL") },
                placeholder = { Text("https://youtube.com/watch?v=...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                trailingIcon = readClipboard?.let { onReadClipboard ->
                    {
                        IconButton(
                            onClick = {
                                onReadClipboard { text ->
                                    if (!text.isNullOrBlank()) {
                                        url = text.trim()
                                        errorMessage = null
                                    } else {
                                        errorMessage =
                                            "Clipboard is unavailable in this Telegram context. " +
                                            "Try launching Mini App from attachment menu."
                                    }
                                }
                            },
                            enabled = !isLoading,
                        ) {
                            Icon(
                                imageVector = TgvdIcons.ContentPaste,
                                contentDescription = "Paste",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (url.isBlank()) {
                        errorMessage = "Please enter a URL"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            client.preview(PreviewRequestDto(url = url)).fold(
                                ifLeft = { errorMessage = it.message ?: "Failed to preview" },
                                ifRight = { result -> navigator.push(PreviewScreen(result)) },
                            )
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isLoading) "Loading..." else "Preview")
            }

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                ErrorCard(message = error)
            }
        }
    }
}
