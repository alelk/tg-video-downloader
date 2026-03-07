package io.github.alelk.tgvd.features.download.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

        var url by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

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
                onValueChange = { url = it; errorMessage = null },
                label = { Text("Video URL") },
                placeholder = { Text("https://youtube.com/watch?v=...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                trailingIcon = {
                    val readClipboard = platformCallbacks.readTextFromClipboard
                    if (readClipboard != null) {
                        IconButton(
                            onClick = {
                                readClipboard { text ->
                                    if (!text.isNullOrBlank()) {
                                        url = text.trim()
                                        errorMessage = null
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
                            val result = client.preview(PreviewRequestDto(url = url))
                            navigator.push(PreviewScreen(result))
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Failed to preview"
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

