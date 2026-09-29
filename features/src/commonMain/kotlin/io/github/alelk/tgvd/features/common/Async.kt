package io.github.alelk.tgvd.features.common

import io.github.alelk.tgvd.api.client.ApiError

/**
 * The three fields every screen state shares — and only those. No generic `UiState<T>` wrapper:
 * screen states differ; what they have in common is exactly this. One name per concept across the
 * module (`busy`, never `submitting`/`saving`).
 */
interface Async {
    /** Loading what the screen shows. Drawn as a loader; existing content stays visible. */
    val loading: Boolean

    /** An action the person started is in flight. Drawn as a disabled button, not a loader. */
    val busy: Boolean get() = false

    val error: ApiError?
}
