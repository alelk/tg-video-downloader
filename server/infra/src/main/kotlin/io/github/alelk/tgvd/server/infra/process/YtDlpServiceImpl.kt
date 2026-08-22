package io.github.alelk.tgvd.server.infra.process

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.system.YtDlpService
import io.github.alelk.tgvd.domain.system.YtDlpVersion
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val logger = KotlinLogging.logger {}

private const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
)

class YtDlpServiceImpl(
    private val config: YtDlpConfig,
    private val httpClient: HttpClient,
) : YtDlpService {

    override suspend fun version(): Either<DomainError, YtDlpVersion> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(listOf(config.path, "--version"))
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                logger.error { "yt-dlp --version failed: $output" }
                DomainError.VideoExtractionFailed(Url("https://yt-dlp.org"), "Failed to get yt-dlp version: $output").left()
            } else {
                YtDlpVersion(version = output).right()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get yt-dlp version" }
            DomainError.VideoExtractionFailed(Url("https://yt-dlp.org"), "yt-dlp not found or not executable: ${e.message}").left()
        }
    }

    override suspend fun latestVersion(): Either<DomainError, YtDlpVersion> = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = httpClient.get(GITHUB_LATEST_RELEASE_URL) {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "tg-video-downloader")
            }
            if (response.status != HttpStatusCode.OK) {
                logger.warn { "GitHub releases API returned ${response.status} for yt-dlp latest release" }
                DomainError.VideoExtractionFailed(
                    Url("https://api.github.com"),
                    "GitHub releases API returned ${response.status}",
                ).left()
            } else {
                YtDlpVersion(version = response.body<GitHubReleaseDto>().tagName).right()
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch latest yt-dlp version from GitHub" }
            DomainError.VideoExtractionFailed(
                Url("https://api.github.com"),
                "Failed to fetch latest yt-dlp version: ${e.message}",
            ).left()
        }
    }

    override suspend fun update(): Either<DomainError, YtDlpVersion> = withContext(Dispatchers.IO) {
        if (!config.allowUpdate) {
            return@withContext DomainError.ValidationError("ytDlp.allowUpdate", "yt-dlp update is disabled by configuration").left()
        }

        try {
            val args = buildList {
                add(config.path)
                add("--update-to")
                add(config.updateChannel)
            }
            logger.info { "Updating yt-dlp to ${config.updateChannel}..." }
            val process = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                logger.error { "yt-dlp update failed: $output" }
                DomainError.VideoExtractionFailed(Url("https://yt-dlp.org"), "Update failed: ${output.takeLast(500)}").left()
            } else {
                logger.info { "yt-dlp updated successfully" }
                version()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to update yt-dlp" }
            DomainError.VideoExtractionFailed(Url("https://yt-dlp.org"), "Update failed: ${e.message}").left()
        }
    }
}

