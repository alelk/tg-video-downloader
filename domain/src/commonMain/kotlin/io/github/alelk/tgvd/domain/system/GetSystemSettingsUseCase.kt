package io.github.alelk.tgvd.domain.system

/** The settings in effect now, secrets included — whoever shows them decides what to hide. */
class GetSystemSettingsUseCase(private val settingsStore: SystemSettingsStore) {
    operator fun invoke(): SystemSettings = settingsStore.current()
}
