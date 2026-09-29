package io.github.alelk.tgvd.domain.track

/** Port: the current [TrackSelectionSettings] (they can change at runtime through the settings API). */
fun interface TrackSelectionSettingsProvider {
    fun trackSelectionSettings(): TrackSelectionSettings
}
