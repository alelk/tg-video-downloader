package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.common.*
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll
import io.kotest.property.Arb
import kotlin.time.Duration.Companion.seconds

class RuleMatchTest : FunSpec({

    fun videoInfo(
        videoId: String = "abc123",
        title: String = "Test Video",
        channelId: String = "UC123",
        channelName: String = "Test Channel",
        webpageUrl: String = "https://youtube.com/watch?v=abc123",
    ) = VideoInfo(
        videoId = VideoId(videoId),
        extractor = Extractor.YOUTUBE,
        title = title,
        channelId = ChannelId(channelId),
        channelName = channelName,
        uploadDate = null,
        duration = 180.seconds,
        webpageUrl = Url(webpageUrl),
    )

    context("ChannelId matching") {
        test("matches exact channel id") {
            val match = RuleMatch.ChannelId("UC123")
            val video = videoInfo(channelId = "UC123")

            match.matches(MatchContext(video)) shouldBe true
        }

        test("does not match different channel id") {
            val match = RuleMatch.ChannelId("UC123")
            val video = videoInfo(channelId = "UC456")

            match.matches(MatchContext(video)) shouldBe false
        }
    }

    context("ChannelName matching") {
        test("matches case-insensitive by default") {
            val match = RuleMatch.ChannelName("test channel")
            val video = videoInfo(channelName = "Test Channel")

            match.matches(MatchContext(video)) shouldBe true
        }

        test("case-sensitive mismatch when ignoreCase=false") {
            val match = RuleMatch.ChannelName("test channel", ignoreCase = false)
            val video = videoInfo(channelName = "Test Channel")

            match.matches(MatchContext(video)) shouldBe false
        }

        test("case-sensitive exact match") {
            val match = RuleMatch.ChannelName("Test Channel", ignoreCase = false)
            val video = videoInfo(channelName = "Test Channel")

            match.matches(MatchContext(video)) shouldBe true
        }
    }

    context("TitleRegex matching") {
        test("matches regex pattern in title") {
            val match = RuleMatch.TitleRegex("S\\d{2}E\\d{2}")
            val video = videoInfo(title = "My Show S01E05 - Episode Title")

            match.matches(MatchContext(video)) shouldBe true
        }

        test("does not match when pattern absent") {
            val match = RuleMatch.TitleRegex("^Season \\d+")
            val video = videoInfo(title = "Episode 5 - Title")

            match.matches(MatchContext(video)) shouldBe false
        }

        test("throws on invalid regex") {
            shouldThrow<IllegalArgumentException> {
                RuleMatch.TitleRegex("[invalid")
            }
        }
    }

    context("UrlRegex matching") {
        test("matches url pattern") {
            val match = RuleMatch.UrlRegex("youtube\\.com")
            val video = videoInfo(webpageUrl = "https://youtube.com/watch?v=x")

            match.matches(MatchContext(video)) shouldBe true
        }

        test("does not match different url") {
            val match = RuleMatch.UrlRegex("rutube\\.ru")
            val video = videoInfo(webpageUrl = "https://youtube.com/watch?v=x")

            match.matches(MatchContext(video)) shouldBe false
        }
    }

    context("AllOf (AND) matching") {
        test("matches when all conditions match") {
            val match = RuleMatch.AllOf(
                listOf(
                    RuleMatch.ChannelName("Rick Astley"),
                    RuleMatch.TitleRegex(".*Never.*"),
                )
            )
            val video = videoInfo(channelName = "Rick Astley", title = "Never Gonna Give You Up")

            match.matches(MatchContext(video)) shouldBe true
        }

        test("does not match when one condition fails") {
            val match = RuleMatch.AllOf(
                listOf(
                    RuleMatch.ChannelName("Rick Astley"),
                    RuleMatch.TitleRegex(".*Together.*"),
                )
            )
            val video = videoInfo(channelName = "Rick Astley", title = "Never Gonna Give You Up")

            match.matches(MatchContext(video)) shouldBe false
        }

        test("throws on empty list") {
            shouldThrow<IllegalArgumentException> {
                RuleMatch.AllOf(emptyList())
            }
        }
    }

    context("AnyOf (OR) matching") {
        test("matches when at least one condition matches") {
            val match = RuleMatch.AnyOf(
                listOf(
                    RuleMatch.ChannelId("UC_other"),
                    RuleMatch.ChannelId("UC123"),
                )
            )
            val video = videoInfo(channelId = "UC123")

            match.matches(MatchContext(video)) shouldBe true
        }

        test("does not match when no condition matches") {
            val match = RuleMatch.AnyOf(
                listOf(
                    RuleMatch.ChannelId("UC_other"),
                    RuleMatch.TitleRegex("^NOPE"),
                )
            )
            val video = videoInfo()

            match.matches(MatchContext(video)) shouldBe false
        }

        test("throws on empty list") {
            shouldThrow<IllegalArgumentException> {
                RuleMatch.AnyOf(emptyList())
            }
        }
    }

    context("specificity") {
        test("ChannelId has highest specificity") {
            RuleMatch.ChannelId("UC123").matchSpecificity() shouldBe 100
            RuleMatch.ChannelName("Name").matchSpecificity() shouldBe 80
            RuleMatch.UrlRegex(".*").matchSpecificity() shouldBe 60
            RuleMatch.TitleRegex(".*").matchSpecificity() shouldBe 40
        }

        test("AllOf takes max of children") {
            val match = RuleMatch.AllOf(
                listOf(
                    RuleMatch.TitleRegex("x"),   // 40
                    RuleMatch.ChannelId("x"),    // 100
                )
            )
            match.matchSpecificity() shouldBe 100
        }

        test("AnyOf takes min of children") {
            val match = RuleMatch.AnyOf(
                listOf(
                    RuleMatch.TitleRegex("x"),   // 40
                    RuleMatch.ChannelId("x"),    // 100
                )
            )
            match.matchSpecificity() shouldBe 40
        }
    }

    context("validation") {
        test("ChannelId rejects blank value") {
            shouldThrow<IllegalArgumentException> {
                RuleMatch.ChannelId("   ")
            }
        }

        test("ChannelName rejects blank value") {
            shouldThrow<IllegalArgumentException> {
                RuleMatch.ChannelName("  ")
            }
        }

        test("UrlRegex rejects invalid pattern") {
            shouldThrow<IllegalArgumentException> {
                RuleMatch.UrlRegex("[bad")
            }
        }
    }

    context("property: matching is deterministic") {
        test("same match + same video = same result") {
            checkAll(Arb.ruleMatch(maxDepth = 2)) { match ->
                val video = videoInfo()
                match.matches(MatchContext(video)) shouldBe match.matches(MatchContext(video))
            }
        }
    }

    context("property: specificity is non-negative") {
        test("specificity >= 0 for any generated match") {
            checkAll(Arb.ruleMatch(maxDepth = 2)) { match ->
                (match.matchSpecificity() >= 0) shouldBe true
            }
        }
    }
})

