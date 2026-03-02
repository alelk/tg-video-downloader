package io.github.alelk.tgvd.features.jobs.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.job.JobDto
import io.github.alelk.tgvd.features.common.component.*
import io.github.alelk.tgvd.features.common.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun JobListScreen() {
    val client = koinInject<TgVideoDownloaderClient>()
    var jobs by remember { mutableStateOf<List<JobDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadJobs() {
        scope.launch {
            try {
                isLoading = jobs.isEmpty()
                val response = client.getJobs()
                jobs = response.items
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load jobs"
            } finally {
                isLoading = false
            }
        }
    }

    // Auto-refresh
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val response = client.getJobs()
                jobs = response.items
                errorMessage = null
            } catch (_: Exception) {}
            isLoading = false
            delay(5000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) { Text("Jobs", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = { loadJobs() }) { Text("Refresh") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        errorMessage?.let {
            ErrorCard(message = it, onRetry = { loadJobs() })
            Spacer(modifier = Modifier.height(8.dp))
        }

        when {
            isLoading && jobs.isEmpty() -> LoadingContent()
            jobs.isEmpty() -> EmptyContent("No jobs yet. Download a video to get started!")
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(jobs, key = { it.id }) { job ->
                        JobCard(job = job, client = client, onRefresh = { loadJobs() })
                    }
                }
            }
        }
    }
}

@Composable
private fun JobCard(job: JobDto, client: TgVideoDownloaderClient, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = job.metadata.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(job.status)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${job.category} • ${job.source.extractor}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Progress
            job.progress?.let { progress ->
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = StatusDownloading,
                )
                progress.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Error
            job.error?.let { error ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${error.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Actions
            val isCancellable = job.status.lowercase() in listOf("pending", "downloading", "post_processing")
            val isRetryable = job.status.lowercase() in listOf("failed", "cancelled")

            if (isCancellable || isRetryable) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (isCancellable) {
                        TextButton(onClick = {
                            scope.launch { runCatching { client.cancelJob(job.id) }; onRefresh() }
                        }) { Text("Cancel") }
                    }
                    if (isRetryable) {
                        TextButton(onClick = {
                            scope.launch { runCatching { client.createJob(
                                io.github.alelk.tgvd.api.contract.job.CreateJobRequestDto(
                                    source = job.source, ruleId = job.ruleId, category = job.category,
                                    videoInfo = io.github.alelk.tgvd.api.contract.video.VideoInfoDto(
                                        videoId = "", extractor = job.source.extractor, title = job.metadata.title,
                                        channelId = "", channelName = "", durationSeconds = 0, webpageUrl = job.source.url,
                                    ),
                                    metadata = job.metadata, storagePlan = job.storagePlan,
                                )
                            ) }; onRefresh() }
                        }) { Text("Retry") }
                    }
                }
            }
        }
    }
}

