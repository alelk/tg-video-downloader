package io.github.alelk.tgvd.features.jobs.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.job.CreateJobRequestDto
import io.github.alelk.tgvd.api.contract.job.JobDto
import io.github.alelk.tgvd.api.contract.video.VideoInfoDto
import io.github.alelk.tgvd.features.common.component.EmptyContent
import io.github.alelk.tgvd.features.common.component.ErrorCard
import io.github.alelk.tgvd.features.common.component.LoadingContent
import io.github.alelk.tgvd.features.common.component.StatusChip
import io.github.alelk.tgvd.features.common.theme.StatusDownloading
import io.github.alelk.tgvd.features.common.util.categoryLabel
import io.github.alelk.tgvd.features.generated.resources.Res
import io.github.alelk.tgvd.features.generated.resources.jobs_phase_convert
import io.github.alelk.tgvd.features.generated.resources.jobs_phase_download
import io.github.alelk.tgvd.features.generated.resources.jobs_phase_processing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
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
                client.getJobs().fold(
                    ifLeft = { errorMessage = it.message ?: "Failed to load jobs" },
                    ifRight = { response ->
                        jobs = response.items
                        errorMessage = null
                    },
                )
            } finally {
                isLoading = false
            }
        }
    }

    // Auto-refresh
    LaunchedEffect(Unit) {
        while (true) {
            // A failed auto-refresh is silent: the list and any error stay as they are
            client.getJobs().onRight { response ->
                jobs = response.items
                errorMessage = null
            }
            isLoading = false
            delay(5000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Jobs", style = MaterialTheme.typography.headlineMedium)
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

            val actualQualityLabel = job.videoInfo.actualFormat?.let { format ->
                val res = if (format.width != null && format.height != null) "${format.height}p" else null
                val codec = format.vcodec?.split(
                    ".",
                )?.firstOrNull()?.replace("avc1", "H264")?.replace("vp09", "VP9")?.replace("hvc1", "H265")?.uppercase()
                listOfNotNull(res, codec).joinToString(" ")
            }

            Text(
                text = listOfNotNull(
                    categoryLabel(job.category),
                    job.source.extractor,
                    actualQualityLabel?.let { if (it.isNotBlank()) "[$it]" else null },
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Progress
            job.progress?.let { progress ->
                Spacer(modifier = Modifier.height(6.dp))
                val phaseLabel = when (progress.phase.lowercase()) {
                    "download" -> stringResource(Res.string.jobs_phase_download)
                    "convert" -> stringResource(Res.string.jobs_phase_convert)
                    else -> stringResource(Res.string.jobs_phase_processing)
                }
                Text(
                    "$phaseLabel: ${progress.percent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { progress.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = StatusDownloading,
                )
                progress.message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                            scope.launch {
                                // The refreshed list shows the outcome; an error is not reported separately
                                client.cancelJob(job.id)
                                onRefresh()
                            }
                        }) { Text("Cancel") }
                    }
                    if (isRetryable) {
                        TextButton(onClick = {
                            scope.launch {
                                // The refreshed list shows the outcome; an error is not reported separately
                                client.createJob(job.toRetryRequest())
                                onRefresh()
                            }
                        }) { Text("Retry") }
                    }
                }
            }
        }
    }
}

/** A new job for the same source, metadata and storage plan as [this] one. */
private fun JobDto.toRetryRequest() = CreateJobRequestDto(
    source = source,
    ruleId = ruleId,
    category = category,
    videoInfo = VideoInfoDto(
        videoId = "",
        extractor = source.extractor,
        title = metadata.title,
        channelId = "",
        channelName = "",
        durationSeconds = 0,
        webpageUrl = source.url,
    ),
    metadata = metadata,
    storagePlan = storagePlan,
)
