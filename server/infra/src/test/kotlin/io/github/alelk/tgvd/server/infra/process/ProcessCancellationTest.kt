package io.github.alelk.tgvd.server.infra.process

import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.storage.AudioFormat
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.server.infra.config.FfmpegConfig
import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.alelk.tgvd.server.infra.db.ExposedTransactionRunner
import io.github.alelk.tgvd.server.infra.service.SystemSettingsHolder
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Cancelling the coroutine that waits for yt-dlp or ffmpeg kills the process and its children. The
 * "binary" is a shell script that records its pid and the pid of a child `sleep 60`, then waits.
 *
 * To see it fail: in `runCancellable` make the killer do nothing (drop `terminateTree`) — the
 * cancelled collector stays blocked on the script's stdout and `cancelAndJoin` times out; drop only
 * the `descendants()` part of `terminateTree` — the child `sleep` survives.
 */
class ProcessCancellationTest :
    FunSpec({
        val workDir = Files.createTempDirectory("tgvd-process-cancel").toFile()
        val scope = CoroutineScope(Dispatchers.Default)

        afterSpec { workDir.deleteRecursively() }

        /** A stand-in binary: writes its pid and its child's pid to [pidFile], prints [line], waits for the child. */
        fun stubBinary(name: String, pidFile: File, line: String): File = File(workDir, name).apply {
            writeText(
                """
                |#!/bin/sh
                |echo $$ > '${pidFile.absolutePath}'
                |sleep 60 &
                |echo $! >> '${pidFile.absolutePath}'
                |echo '$line'
                |wait
                """.trimMargin() + "\n",
            )
            setExecutable(true)
        }

        suspend fun startedPids(pidFile: File): List<Long> = eventually(10.seconds) {
            val pids = pidFile.takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }.orEmpty()
            pids shouldHaveSize 2
            pids.map { it.trim().toLong() }
        }

        fun isAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

        test("yt-dlp download: cancelling the collector kills yt-dlp and its children") {
            val pidFile = File(workDir, "ytdlp.pids")
            val script = stubBinary("yt-dlp", pidFile, "[download]   1.0% of 10.00MiB at 1.00MiB/s ETA 00:09")
            val db = PostgresTestContainer.newMigratedDatabase().database
            val settings =
                SystemSettingsHolder(
                    YtDlpConfig(path = script.absolutePath),
                    ProxyConfig(),
                    ExposedTransactionRunner(db),
                    Clock.System,
                )
            val runner = YtDlpRunner(settings)
            val download =
                scope.launch {
                    runner.downloadWithProgress(
                        Url("https://www.youtube.com/watch?v=cancel-me"),
                        FilePath(File(workDir, "out/video.mkv").absolutePath),
                        DownloadPolicy(),
                        videoInfo = null,
                        mediaSelection = null,
                    ).collect { }
                }

            val pids = startedPids(pidFile)
            pids.forEach { isAlive(it) shouldBe true }

            withTimeout(15.seconds) { download.cancelAndJoin() }

            pids.forEach { isAlive(it) shouldBe false }
        }

        test("ffmpeg: cancelling the caller kills ffmpeg and its children") {
            val pidFile = File(workDir, "ffmpeg.pids")
            val script = stubBinary("ffmpeg", pidFile, "frame=1")
            val runner = FfmpegRunner(FfmpegConfig(path = script.absolutePath))
            val conversion =
                scope.launch {
                    runner.extractAudio(
                        FilePath(File(workDir, "in.mkv").absolutePath),
                        FilePath(File(workDir, "out.m4a").absolutePath),
                        AudioFormat.M4A,
                    )
                }

            val pids = startedPids(pidFile)
            pids.forEach { isAlive(it) shouldBe true }

            withTimeout(15.seconds) { conversion.cancelAndJoin() }

            pids.forEach { isAlive(it) shouldBe false }
        }
    })
