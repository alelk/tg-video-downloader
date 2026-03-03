package io.github.alelk.tgvd.server.infra.process

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.job.JobPhase
import io.github.alelk.tgvd.domain.storage.AudioFormat
import io.github.alelk.tgvd.domain.storage.MediaContainer
import io.github.alelk.tgvd.server.infra.config.FfmpegConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}

class FfmpegRunner(
    private val config: FfmpegConfig,
) {

    suspend fun convertVideo(
        input: FilePath,
        output: FilePath,
        container: MediaContainer,
    ): Either<DomainError, FilePath> = runFfmpeg(
        args = listOf("-i", input.value, "-c:v", "copy", "-c:a", "copy", "-y", output.value),
        description = "convert to ${container.extension}",
    ).map { output }

    suspend fun extractAudio(
        input: FilePath,
        output: FilePath,
        format: AudioFormat,
    ): Either<DomainError, FilePath> = runFfmpeg(
        args = listOf("-i", input.value, "-vn", "-c:a", audioCodecFor(format), "-y", output.value),
        description = "extract audio as ${format.extension}",
    ).map { output }

    suspend fun embedMetadata(
        input: FilePath,
        output: FilePath,
        metadata: Map<String, String>,
    ): Either<DomainError, FilePath> {
        val metadataArgs = metadata.flatMap { (key, value) -> listOf("-metadata", "$key=$value") }
        return runFfmpeg(
            args = listOf("-i", input.value) + metadataArgs + listOf("-c", "copy", "-y", output.value),
            description = "embed metadata",
        ).map { output }
    }

    suspend fun embedThumbnail(
        input: FilePath,
        thumbnail: FilePath,
        output: FilePath,
    ): Either<DomainError, FilePath> {
        val thumbExt = thumbnail.extension.lowercase()
        val outputExt = output.extension.lowercase()
        val isMp4 = outputExt == "mp4" || outputExt == "m4a" || outputExt == "m4v"

        // For MP4: webp thumbnails must be converted; use mjpeg codec for the cover art stream
        val args = buildList {
            add("-i"); add(input.value)
            add("-i"); add(thumbnail.value)
            add("-map"); add("0")
            add("-map"); add("1")
            add("-c"); add("copy")
            if (isMp4) {
                // MP4 doesn't support webp covers; transcode thumbnail to mjpeg
                if (thumbExt == "webp" || thumbExt == "png") {
                    add("-c:v:1"); add("mjpeg")
                    add("-q:v:1"); add("2")  // high quality jpeg
                } else {
                    add("-c:v:1"); add("copy")
                }
                add("-disposition:v:1"); add("attached_pic")
            } else {
                add("-disposition:v:1"); add("attached_pic")
            }
            add("-y"); add(output.value)
        }

        return runFfmpeg(args = args, description = "embed thumbnail").map { output }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun runFfmpeg(
        args: List<String>,
        description: String,
    ): Either<DomainError, Unit> = withContext(Dispatchers.IO) {
        try {
            val command = listOf(config.path) + args
            logger.info { "Running ffmpeg ($description): ${command.joinToString(" ")}" }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                logger.error { "ffmpeg failed (exit=$exitCode, $description): ${output.takeLast(500)}" }
                DomainError.PostProcessingFailed(
                    jobId = JobId(kotlin.uuid.Uuid.random()),
                    phase = JobPhase.CONVERT,
                    cause = output.takeLast(500),
                ).left()
            } else {
                Unit.right()
            }
        } catch (e: Exception) {
            logger.error(e) { "ffmpeg execution failed ($description)" }
            DomainError.PostProcessingFailed(
                jobId = JobId(kotlin.uuid.Uuid.random()),
                phase = JobPhase.CONVERT,
                cause = e.message ?: "Unknown error",
            ).left()
        }
    }

    private fun audioCodecFor(format: AudioFormat): String = when (format) {
        AudioFormat.M4A -> "aac"
        AudioFormat.MP3 -> "libmp3lame"
        AudioFormat.OPUS -> "libopus"
        AudioFormat.FLAC -> "flac"
        AudioFormat.WAV -> "pcm_s16le"
    }
}

