package io.github.alelk.tgvd.features.common

import arrow.core.Either
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.alelk.tgvd.api.client.ApiError
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base of a screen's state holder (G12 / Fork 3: a Voyager [ScreenModel], acquired with `koinScreenModel`
 * in the screen's Entry): one [state] flow, one-shot [effects] delivered exactly once through a [Channel],
 * and one "raise flag → call → fold `Either` → lower flag" block, [runRequest].
 *
 * Written by hand per call, that block has one recurring bug: a failure (or a cancellation) forgets to
 * lower the flag and the screen spins forever. Here the flag is lowered on every outcome.
 *
 * No Compose imports: the Entry collects [state] and [effects]; the Content renders the state.
 */
abstract class FeatureScreenModel<S : Async, E : Any>(initialState: S) : ScreenModel {
    protected val mutableState: MutableStateFlow<S> = MutableStateFlow(initialState)
    val state: StateFlow<S> = mutableState.asStateFlow()

    private val mutableEffects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = mutableEffects.receiveAsFlow()

    protected fun sendEffect(effect: E) {
        mutableEffects.trySend(effect)
    }

    /**
     * Runs [call] in [screenModelScope] (cancelled when the screen leaves the navigator) — see [runRequest].
     * Returns the [Job] so a caller can cancel an earlier, still running attempt.
     */
    protected fun <T> launchRequest(
        call: suspend () -> Either<ApiError, T>,
        started: (S) -> S,
        finished: (S) -> S,
        onFailure: (S, ApiError) -> S,
        onSuccess: (S, T) -> S,
    ): Job = screenModelScope.launch { runRequest(call, started, finished, onFailure, onSuccess) }

    /**
     * Applies [started] (raises a flag), runs [call], then applies [finished] (lowers it) on BOTH outcomes —
     * and on cancellation. [onFailure]/[onSuccess] receive the state with the flag already lowered.
     * Separate `started`/`finished` (rather than one `busy` switch) because a screen may have several
     * independent requests in flight, each with its own indicator.
     *
     * [onFailure]/[onSuccess] may run more than once (a state update retries on contention), so they must
     * be pure; side effects such as [sendEffect] go after the call, on the returned result.
     */
    protected suspend fun <T> runRequest(
        call: suspend () -> Either<ApiError, T>,
        started: (S) -> S,
        finished: (S) -> S,
        onFailure: (S, ApiError) -> S,
        onSuccess: (S, T) -> S,
    ): Either<ApiError, T> {
        mutableState.update(started)
        var settled = false
        try {
            val result = call()
            settled = true
            mutableState.update { current ->
                result.fold(
                    ifLeft = { error -> onFailure(finished(current), error) },
                    ifRight = { value -> onSuccess(finished(current), value) },
                )
            }
            return result
        } finally {
            if (!settled) mutableState.update(finished)
        }
    }
}
