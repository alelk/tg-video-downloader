package io.github.alelk.tgvd.domain.storage

/**
 * Описание одного выходного файла правила.
 *
 * Каждый [OutputRule] — самодостаточная единица:
 * путь + формат + качество + пост-обработка.
 *
 * Первый output в `Rule.outputs` — оригинальный файл (как скачано yt-dlp).
 * Остальные — конвертации/копии с индивидуальными настройками.
 *
 * @param pathTemplate шаблон пути: `/media/Music/{artist}/{title}.{ext}`
 * @param format формат выходного файла (OriginalVideo, ConvertedVideo, Audio, Thumbnail)
 * @param maxQuality макс. качество для этого output (null = оригинальное качество без понижения)
 * @param encodeSettings настройки кодирования видео (кодек, CRF, preset, HW accel). null = defaults
 * @param embedThumbnail встраивать обложку в этот файл
 * @param embedMetadata встраивать теги (title, artist, album) в контейнер
 * @param embedSubtitles встраивать субтитры в контейнер
 * @param normalizeAudio нормализация громкости аудио
 */
data class OutputRule(
    val pathTemplate: String,
    val format: OutputFormat,
    val maxQuality: DownloadPolicy.VideoQuality? = null,
    val encodeSettings: VideoEncodeSettings? = null,
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = false,
    val embedSubtitles: Boolean = false,
    val normalizeAudio: Boolean = false,
) {
    init {
        require(pathTemplate.isNotBlank()) { "Path template must not be blank" }
    }
}

