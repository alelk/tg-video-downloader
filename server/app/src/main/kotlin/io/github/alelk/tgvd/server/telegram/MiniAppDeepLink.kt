package io.github.alelk.tgvd.server.telegram

import java.nio.charset.StandardCharsets
import java.util.Base64

internal object MiniAppDeepLink {
    private val urlRegex = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    fun buildLaunchUrl(botUsername: String, appShortName: String, rawUrl: String): String {
        val normalizedBot = botUsername.trim().trimStart('@')
        val normalizedApp = appShortName.trim().trimStart('/')
        val encoded = encodeStartParam(rawUrl)
        return "https://t.me/$normalizedBot/$normalizedApp?startapp=$encoded"
    }

    /**
     * Кодирует URL в base64url для передачи через startapp.
     * Убирает схему (https:// / http://) перед кодированием — экономит ~10 символов.
     * Лимит Telegram на startapp = 64 символа.
     * Mini App восстанавливает схему в decodePrefilledUrl().
     */
    fun encodeStartParam(rawUrl: String): String {
        val stripped = rawUrl
            .removePrefix("https://")
            .removePrefix("http://")
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(stripped.toByteArray(StandardCharsets.UTF_8))
    }

    fun extractFirstUrl(text: String): String? {
        val match = urlRegex.find(text) ?: return null
        val candidate = match.value.trimEnd(')', ']', '}', ',', '.', ';', ':')
        return candidate.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
    }

    /**
     * Проверяет URL по списку regex-паттернов.
     * Если список пуст — возвращает true (реагируем на любой URL).
     */
    fun matchesUrlPatterns(url: String, patterns: List<Regex>): Boolean {
        if (patterns.isEmpty()) return true
        return patterns.any { it.containsMatchIn(url) }
    }
}

