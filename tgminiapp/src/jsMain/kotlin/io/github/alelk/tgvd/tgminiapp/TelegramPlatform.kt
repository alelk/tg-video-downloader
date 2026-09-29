package io.github.alelk.tgvd.tgminiapp

import com.kirillNay.telegram.miniapp.webApp.webApp
import io.github.alelk.tgvd.features.common.theme.PlatformCallbacks

/** Telegram WebApp / browser implementations of the [PlatformCallbacks] the features module uses. */
internal fun createPlatformCallbacks() = PlatformCallbacks(
    onHapticFeedback = {
        try {
            webApp.hapticFeedback.impactOccurred("light")
        } catch (_: Throwable) {
        }
    },
    prefilledUrl = resolvePrefilledUrl(),
    readTextFromClipboard = { callback ->
        readClipboardText(callback)
    },
)

private fun resolvePrefilledUrl(): String? {
    val startParam =
        try {
            webApp.initDataUnsafe.startParam
        } catch (_: Throwable) {
            null
        }
    val urlSearch =
        try {
            js("window.location.search") as? String
        } catch (_: Throwable) {
            null
        }
    console.log("[tgvd] resolvePrefilledUrl: startParam=$startParam, search=$urlSearch")

    val candidates =
        listOfNotNull(
            // Preferred Telegram-provided parameter for deep links.
            startParam,
            readQueryParam("tgWebAppStartParam"),
            // Useful fallback if bot passes custom query params.
            readQueryParam("url"),
            readQueryParam("video_url"),
            readQueryParam("videoUrl"),
        )

    console.log("[tgvd] resolvePrefilledUrl candidates: $candidates")

    return candidates
        .asSequence()
        .mapNotNull { decodePrefilledUrl(it) }
        .firstOrNull()
        .also { console.log("[tgvd] resolvePrefilledUrl result: $it") }
}

private fun readQueryParam(name: String): String? = try {
    val search = js("window.location.search") as? String ?: ""
    if (search.isBlank()) {
        null
    } else {
        val params = js("new URLSearchParams(search)")
        val value = params.get(name) as? String
        value?.trim()?.takeIf { it.isNotBlank() }
    }
} catch (_: Throwable) {
    null
}

/**
 * A plain http(s) URL, a URI-encoded one, or base64url (optionally URI-encoded). The server strips
 * `https://` before base64-encoding (the `startapp` parameter is limited to 64 chars), so a decoded
 * value without a scheme gets `https://` back.
 */
private fun decodePrefilledUrl(raw: String): String? {
    val value = raw.trim().takeIf { it.isNotBlank() } ?: return null
    val decodedComponent = decodeUriComponentSafely(value)
    return sequenceOf(value, decodedComponent).firstOrNull(::isHttpUrl)
        ?: (decodeBase64UrlSafely(value) ?: decodeBase64UrlSafely(decodedComponent))
            ?.let { decoded -> if (isHttpUrl(decoded)) decoded else "https://$decoded" }
}

private fun isHttpUrl(value: String?): Boolean {
    val url = value?.trim() ?: return false
    return url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
}

private fun decodeUriComponentSafely(value: String): String = try {
    (js("decodeURIComponent") as (String) -> String)(value)
} catch (_: Throwable) {
    value
}

private fun decodeBase64UrlSafely(value: String): String? = try {
    val normalized = value.replace('-', '+').replace('_', '/')
    val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
    ((js("atob") as (String) -> String)(padded)).trim().takeIf { it.isNotBlank() }
} catch (_: Throwable) {
    null
}

/**
 * Reads text from clipboard using Telegram WebApp typed API (preferred on iOS).
 * Falls back to navigator.clipboard Web API for desktop browsers.
 *
 * Note: Telegram's readTextFromClipboard can only be called in response to user interaction
 * (e.g. a click) and requires Bot API 6.4+.
 */
private fun readClipboardText(callback: (String?) -> Unit) {
    // In Telegram Mini App, prefer Telegram API only.
    // navigator.clipboard is often blocked in WebView and throws NotAllowedError.
    val telegramWebApp: dynamic =
        try {
            val telegram: dynamic = js("window.Telegram")
            telegram?.WebApp
        } catch (_: Throwable) {
            null
        }

    if (telegramWebApp != null && telegramWebApp.readTextFromClipboard != null) {
        try {
            telegramWebApp.readTextFromClipboard { text: dynamic -> callback(clipboardValue(text)) }
        } catch (_: Throwable) {
            callback(null)
        }
    } else {
        readBrowserClipboardText(callback)
    }
}

/** Non-Telegram fallback: regular browsers/dev mode. */
private fun readBrowserClipboardText(callback: (String?) -> Unit) {
    val clipboard: dynamic =
        try {
            js("navigator.clipboard")
        } catch (_: Throwable) {
            null
        }
    if (clipboard != null && clipboard != undefined) {
        try {
            clipboard
                .readText()
                .then { text: dynamic -> callback(clipboardValue(text)) }
                .catch { _: dynamic -> callback(null) }
        } catch (_: Throwable) {
            callback(null)
        }
    } else {
        callback(null)
    }
}

private fun clipboardValue(text: dynamic): String? = (text as? String)?.trim()?.takeIf { it.isNotBlank() }
