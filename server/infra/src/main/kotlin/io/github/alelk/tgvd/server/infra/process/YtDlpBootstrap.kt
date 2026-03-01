package io.github.alelk.tgvd.server.infra.process

import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

private val logger = KotlinLogging.logger {}

/**
 * Ensures yt-dlp binary is available before the application starts processing jobs.
 *
 * If the binary is not found at [YtDlpConfig.path] and [YtDlpConfig.autoDownload] is enabled,
 * downloads it from the official GitHub release.
 */
class YtDlpBootstrap(private val config: YtDlpConfig) {

    suspend fun ensureAvailable() {
        if (isAvailable()) {
            val version = queryVersion()
            logger.info { "yt-dlp found: $version" }
            return
        }

        if (!config.autoDownload) {
            logger.warn { "yt-dlp not found at '${config.path}' and autoDownload is disabled" }
            return
        }

        logger.info { "yt-dlp not found at '${config.path}', downloading..." }
        download()
    }

    private suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(listOf(config.path, "--version"))
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun queryVersion(): String = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(listOf(config.path, "--version"))
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { it.readText().trim() }
        } catch (_: Exception) {
            "unknown"
        }
    }

    private suspend fun download() = withContext(Dispatchers.IO) {
        val targetFile = File(config.path)
        val downloadUrl = resolveDownloadUrl()

        logger.info { "Downloading yt-dlp from $downloadUrl → ${targetFile.absolutePath}" }

        try {
            // Ensure parent directory exists
            targetFile.parentFile?.mkdirs()

            // Download binary
            URI(downloadUrl).toURL().openStream().use { input ->
                targetFile.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    logger.info { "Downloaded ${bytes / 1024} KB" }
                }
            }

            // Make executable (Unix)
            targetFile.setExecutable(true)

            // Verify
            if (isAvailable()) {
                val version = queryVersion()
                logger.info { "yt-dlp downloaded successfully: $version" }
            } else {
                logger.error { "yt-dlp downloaded but not executable. Check path: ${targetFile.absolutePath}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to download yt-dlp from $downloadUrl" }
            // Clean up partial download
            targetFile.delete()
        }
    }

    companion object {
        private const val GITHUB_RELEASE_BASE = "https://github.com/yt-dlp/yt-dlp/releases/latest/download"

        fun resolveDownloadUrl(): String {
            val os = System.getProperty("os.name", "").lowercase()
            val arch = System.getProperty("os.arch", "").lowercase()

            return when {
                os.contains("mac") || os.contains("darwin") -> "$GITHUB_RELEASE_BASE/yt-dlp_macos"
                os.contains("win") -> "$GITHUB_RELEASE_BASE/yt-dlp.exe"
                os.contains("linux") && arch.contains("aarch64") -> "$GITHUB_RELEASE_BASE/yt-dlp_linux_aarch64"
                else -> "$GITHUB_RELEASE_BASE/yt-dlp_linux"
            }
        }
    }
}

