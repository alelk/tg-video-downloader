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
import io.github.alelk.tgvd.domain.storage.VideoEncodeSettings
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
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        encodeSettings: VideoEncodeSettings? = null,
    ): Either<DomainError, FilePath> {
        val settings = encodeSettings ?: VideoEncodeSettings()

        // Determine if transcoding is actually needed:
        // If maxHeight is set, check source resolution — skip transcoding if source is already within limits
        val sourceResolution = if (maxHeight != null || maxWidth != null) probeVideoResolution(input) else null
        val sourceWidth = sourceResolution?.first
        val sourceHeight = sourceResolution?.second
        val needsTranscode = when {
            maxHeight == null && maxWidth == null -> false
            sourceHeight == null || sourceWidth == null -> true // can't determine — transcode to be safe
            maxHeight != null && sourceHeight > maxHeight -> true
            maxWidth != null && sourceWidth > maxWidth -> true
            else -> false
        }

        if ((maxHeight != null || maxWidth != null) && !needsTranscode) {
            logger.info { "Source resolution (${sourceWidth}x${sourceHeight}) within limits (${maxWidth ?: "any"}x${maxHeight ?: "any"}), will remux only" }
        }

        val args = buildList {
            // HW accel init args (must come before -i)
            if (needsTranscode) {
                settings.hwAccel?.let { hw ->
                    when (hw) {
                        VideoEncodeSettings.HwAccel.VIDEOTOOLBOX -> {} // no init flag needed
                        VideoEncodeSettings.HwAccel.NVENC -> { add("-hwaccel"); add("cuda") }
                        VideoEncodeSettings.HwAccel.QSV -> { add("-hwaccel"); add("qsv") }
                        VideoEncodeSettings.HwAccel.VAAPI -> { add("-hwaccel"); add("vaapi"); add("-hwaccel_output_format"); add("vaapi") }
                        VideoEncodeSettings.HwAccel.AMF -> {} // no init flag needed
                    }
                }
            }

            add("-i"); add(input.value)

            if (needsTranscode) {
                // Video scaling — fit within maxWidth x maxHeight box, preserving aspect ratio.
                // scale=iw*min(1,min(MW/iw,MH/ih)):ih*min(1,min(MW/iw,MH/ih))
                // This scales down only (never upscales) and constrains BOTH dimensions.
                // -2 trick ensures dimensions are divisible by 2 for codec compatibility.
                val scaleFilter = buildScaleFilter(maxWidth, maxHeight)
                add("-vf"); add(scaleFilter)
                // Video encoder
                add("-c:v"); add(settings.resolveEncoder())
                // Quality
                val isHw = settings.hwAccel != null
                if (isHw) {
                    when (settings.hwAccel) {
                        VideoEncodeSettings.HwAccel.VIDEOTOOLBOX -> {
                            // VideoToolbox uses -q:v with INVERTED scale: 1 = worst, 100 = best (lossless)
                            // CRF uses: 0 = lossless, 51 = worst
                            // Convert: q:v = round((51 - crf) / 51 * 99) + 1
                            val vtQuality = ((51 - settings.crf).toDouble() / 51.0 * 99.0 + 1.0).toInt().coerceIn(1, 100)
                            add("-q:v"); add("$vtQuality")
                        }
                        VideoEncodeSettings.HwAccel.NVENC -> {
                            // NVENC -cq uses same 0-51 scale as CRF (0 = lossless, 51 = worst)
                            add("-cq"); add("${settings.crf}")
                            // Map user preset to NVENC p1-p7 scale
                            add("-preset"); add(nvencPreset(settings.preset))
                        }
                        VideoEncodeSettings.HwAccel.QSV -> { add("-global_quality"); add("${settings.crf}") }
                        else -> { add("-crf"); add("${settings.crf}") }
                    }
                } else {
                    add("-crf"); add("${settings.crf}")
                    add("-preset"); add(settings.preset.ffmpegValue)
                }
                // Audio
                add("-c:a"); add(settings.resolveAudioCodec(container))
                add("-b:a"); add(settings.audioBitrate)
            } else {
                // No transcoding — just remux
                add("-c:v"); add("copy")
                add("-c:a"); add("copy")
            }
            add("-y"); add(output.value)
        }

        val desc = if (needsTranscode) {
            val encoder = settings.resolveEncoder()
            val sizeDesc = listOfNotNull(maxWidth?.let { "${it}w" }, maxHeight?.let { "${it}p" }).joinToString("x")
            "convert to ${container.extension} ($sizeDesc, $encoder, crf=${settings.crf})"
        } else {
            val limits = listOfNotNull(maxWidth?.let { "${it}w" }, maxHeight?.let { "${it}p" }).joinToString("x")
            "remux to ${container.extension}" + if (limits.isNotEmpty()) " (source ${sourceWidth}x${sourceHeight} within $limits)" else ""
        }

        return runFfmpeg(args = args, description = desc).map { output }
    }

    /**
     * Build ffmpeg scale filter that fits video within maxWidth x maxHeight box.
     * Never upscales. Ensures dimensions are divisible by 2.
     */
    private fun buildScaleFilter(maxWidth: Int?, maxHeight: Int?): String {
        // Use ffmpeg expressions to constrain both dimensions while preserving aspect ratio.
        // The approach: compute scale factor as min(1, min(MW/iw, MH/ih)), apply to both dims, round to even.
        return when {
            maxWidth != null && maxHeight != null -> {
                // Fit within box: scale down only, preserve aspect ratio, round to even
                "scale='trunc(iw*min(1\\,min($maxWidth/iw\\,$maxHeight/ih))/2)*2':'trunc(ih*min(1\\,min($maxWidth/iw\\,$maxHeight/ih))/2)*2'"
            }
            maxHeight != null -> {
                // Constrain height only (original behavior)
                "scale=-2:'min($maxHeight\\,ih)'"
            }
            maxWidth != null -> {
                // Constrain width only
                "scale='min($maxWidth\\,iw)':-2"
            }
            else -> error("No dimension constraint specified")
        }
    }

    /**
     * Map generic EncodePreset to NVENC preset names (p1=fastest .. p7=slowest).
     */
    private fun nvencPreset(preset: VideoEncodeSettings.EncodePreset): String = when (preset) {
        VideoEncodeSettings.EncodePreset.ULTRAFAST -> "p1"
        VideoEncodeSettings.EncodePreset.SUPERFAST -> "p1"
        VideoEncodeSettings.EncodePreset.VERYFAST -> "p2"
        VideoEncodeSettings.EncodePreset.FASTER -> "p3"
        VideoEncodeSettings.EncodePreset.FAST -> "p4"
        VideoEncodeSettings.EncodePreset.MEDIUM -> "p4"
        VideoEncodeSettings.EncodePreset.SLOW -> "p5"
        VideoEncodeSettings.EncodePreset.SLOWER -> "p6"
        VideoEncodeSettings.EncodePreset.VERYSLOW -> "p7"
    }

    /**
     * Probe video width and height using ffprobe. Returns (width, height) pair or null if detection fails.
     */
    private suspend fun probeVideoResolution(input: FilePath): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        try {
            val ffprobePath = config.path.replace("ffmpeg", "ffprobe")
            val process = ProcessBuilder(
                ffprobePath, "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height",
                "-of", "csv=p=0",
                input.value,
            ).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                // Output format: "width,height" e.g. "2579,1080"
                val parts = output.lines().firstOrNull()?.trim()?.split(",")
                val width = parts?.getOrNull(0)?.trim()?.toIntOrNull()
                val height = parts?.getOrNull(1)?.trim()?.toIntOrNull()
                if (width != null && height != null) {
                    logger.info { "Probed video resolution: ${width}x${height} for ${input.fileName}" }
                    width to height
                } else {
                    logger.warn { "Could not parse ffprobe resolution output: $output" }
                    null
                }
            } else {
                logger.warn { "ffprobe failed (exit=$exitCode): $output" }
                null
            }
        } catch (e: Exception) {
            logger.warn { "ffprobe not available: ${e.message}" }
            null
        }
    }

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

