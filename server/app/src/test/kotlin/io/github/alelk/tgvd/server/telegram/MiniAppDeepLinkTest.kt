package io.github.alelk.tgvd.server.telegram

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

class MiniAppDeepLinkTest : StringSpec({

    "build launch url encodes startapp without scheme" {
        val sourceUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ"

        val launch = MiniAppDeepLink.buildLaunchUrl(
            botUsername = "@my_bot",
            appShortName = "downloader",
            rawUrl = sourceUrl,
        )

        // URL без https:// = "youtube.com/watch?v=dQw4w9WgXcQ" → base64url
        launch.shouldStartWith("https://t.me/my_bot/downloader?startapp=")
        val encoded = MiniAppDeepLink.encodeStartParam(sourceUrl)
        launch.shouldContain("startapp=$encoded")
    }

    "encode start param strips https scheme" {
        val encoded = MiniAppDeepLink.encodeStartParam("https://youtube.com/watch?v=abc")
        // должен кодировать "youtube.com/watch?v=abc", без "https://"
        val decoded = String(java.util.Base64.getUrlDecoder().decode(encoded))
        decoded shouldBe "youtube.com/watch?v=abc"
    }

    "encode start param strips http scheme" {
        val encoded = MiniAppDeepLink.encodeStartParam("http://example.com/video")
        val decoded = String(java.util.Base64.getUrlDecoder().decode(encoded))
        decoded shouldBe "example.com/video"
    }

    "extract first url trims punctuation" {
        val text = "check this: https://youtu.be/dQw4w9WgXcQ), thanks"

        val url = MiniAppDeepLink.extractFirstUrl(text)

        url shouldBe "https://youtu.be/dQw4w9WgXcQ"
    }

    "extract first url returns null when absent" {
        MiniAppDeepLink.extractFirstUrl("hello there").shouldBeNull()
    }

    "matches url patterns returns true when list is empty" {
        MiniAppDeepLink.matchesUrlPatterns("https://example.com/video", emptyList()).shouldBeTrue()
    }

    "matches url patterns returns true on match" {
        val patterns = listOf(Regex("youtube\\.com"), Regex("youtu\\.be"))
        MiniAppDeepLink.matchesUrlPatterns("https://youtube.com/watch?v=1", patterns).shouldBeTrue()
        MiniAppDeepLink.matchesUrlPatterns("https://youtu.be/abc", patterns).shouldBeTrue()
    }

    "matches url patterns returns false when no match" {
        val patterns = listOf(Regex("youtube\\.com"), Regex("youtu\\.be"))
        MiniAppDeepLink.matchesUrlPatterns("https://rutube.ru/video/123", patterns).shouldBeFalse()
        MiniAppDeepLink.matchesUrlPatterns("https://vk.com/video", patterns).shouldBeFalse()
    }

    "matches url patterns is case insensitive" {
        val patterns = listOf(Regex("youtube\\.com", RegexOption.IGNORE_CASE))
        MiniAppDeepLink.matchesUrlPatterns("https://YOUTUBE.COM/watch?v=1", patterns).shouldBeTrue()
    }
})


