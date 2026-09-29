package io.github.alelk.tgvd.server.infra.process

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/** How long a process gets to exit after `SIGTERM` before it is killed. */
internal val PROCESS_TERMINATION_GRACE: Duration = 5.seconds

/**
 * Runs [block] against this started process and ties the process to the calling coroutine: when the
 * coroutine is cancelled — or [block] ends in any other way while the process is still alive — the
 * whole process tree is terminated ([terminateTree]).
 *
 * [block] may read the process output with blocking calls on an IO thread: terminating the process
 * closes its pipes, so a blocked `readLine`/`readText` returns and the read loop ends. Wait for the
 * exit with [awaitExit], never with a blocking `waitFor()`.
 */
@Suppress("TooGenericExceptionCaught") // rethrown as is, or replaced by the cancellation that caused it
internal suspend fun <T> Process.runCancellable(
    grace: Duration = PROCESS_TERMINATION_GRACE,
    block: suspend CoroutineScope.(Process) -> T,
): T = coroutineScope {
    val process = this@runCancellable
    // Started undispatched: it is suspended in awaitCancellation() before block runs, so a cancellation
    // at any point after this line reaches the finally below.
    val killer =
        launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                if (process.isAlive) process.terminateTree(grace)
            }
        }
    try {
        block(process)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A read of a pipe that the termination closed fails with an IOException; when that is the
        // reason, report the cancellation, not the read failure.
        ensureActive()
        throw e
    } finally {
        killer.cancel()
    }
}

/** Suspends until the process exits and returns its exit code; cancellable (unlike `waitFor()`). */
internal suspend fun Process.awaitExit(): Int = onExit().await().exitValue()

/**
 * Terminates this process and every descendant (yt-dlp starts ffmpeg): `SIGTERM` to the descendants
 * and to the process, up to [grace] for all of them to exit, then `SIGKILL` to whatever is still alive
 * (and up to [grace] more for it to die). The descendants are collected first — once the parent dies
 * they are no longer its descendants. Blocking; safe to call on an already finished process.
 */
internal fun Process.terminateTree(grace: Duration = PROCESS_TERMINATION_GRACE) {
    val tree = descendants().toList() + toHandle()
    logger.info { "Terminating process ${pid()} and ${tree.size - 1} descendant(s)" }
    tree.forEach { it.destroy() }
    val deadline = System.nanoTime() + grace.inWholeNanoseconds
    val survivors = tree.filterNot { it.awaitExitUntil(deadline) }
    if (survivors.isNotEmpty()) {
        logger.warn { "${survivors.size} process(es) of ${pid()} ignored SIGTERM for $grace; killing them" }
        survivors.forEach { it.destroyForcibly() }
        val killDeadline = System.nanoTime() + grace.inWholeNanoseconds
        survivors.forEach { it.awaitExitUntil(killDeadline) }
    }
}

/** Waits (blocking) for the process to exit until [deadlineNanos]; true when it has exited. */
private fun ProcessHandle.awaitExitUntil(deadlineNanos: Long): Boolean {
    val remaining = (deadlineNanos - System.nanoTime()).coerceAtLeast(0)
    return try {
        onExit().get(remaining, TimeUnit.NANOSECONDS)
        true
    } catch (timeout: TimeoutException) {
        logger.debug(timeout) { "Process ${pid()} still running at the deadline" }
        !isAlive
    }
}
