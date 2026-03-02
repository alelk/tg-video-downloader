package io.github.alelk.tgvd.domain.metadata

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

fun Arb.Companion.metadataTemplateMusicVideo(
    artistOverride: Arb<String?> = Arb.string(2..15, Codepoint.az()).orNull(0.5),
    artistPattern: Arb<String?> = Arb.constant(null),
    titleOverride: Arb<String?> = Arb.string(2..20, Codepoint.az()).orNull(0.6),
    titlePattern: Arb<String?> = Arb.constant(null),
    defaultTags: Arb<List<String>> = Arb.list(Arb.string(2..10, Codepoint.az()), 0..3),
): Arb<MetadataTemplate.MusicVideo> = arbitrary {
    MetadataTemplate.MusicVideo(
        artistOverride = artistOverride.bind(),
        artistPattern = artistPattern.bind(),
        titleOverride = titleOverride.bind(),
        titlePattern = titlePattern.bind(),
        defaultTags = defaultTags.bind(),
    )
}

fun Arb.Companion.metadataTemplateSeriesEpisode(
    seriesNameOverride: Arb<String?> = Arb.string(2..15, Codepoint.az()).orNull(0.5),
    seasonPattern: Arb<String?> = Arb.constant(null),
    episodePattern: Arb<String?> = Arb.constant(null),
    titleOverride: Arb<String?> = Arb.string(2..20, Codepoint.az()).orNull(0.6),
    titlePattern: Arb<String?> = Arb.constant(null),
    defaultTags: Arb<List<String>> = Arb.list(Arb.string(2..10, Codepoint.az()), 0..3),
): Arb<MetadataTemplate.SeriesEpisode> = arbitrary {
    MetadataTemplate.SeriesEpisode(
        seriesNameOverride = seriesNameOverride.bind(),
        seasonPattern = seasonPattern.bind(),
        episodePattern = episodePattern.bind(),
        titleOverride = titleOverride.bind(),
        titlePattern = titlePattern.bind(),
        defaultTags = defaultTags.bind(),
    )
}

fun Arb.Companion.metadataTemplateOther(
    titleOverride: Arb<String?> = Arb.string(2..20, Codepoint.az()).orNull(0.6),
    titlePattern: Arb<String?> = Arb.constant(null),
    defaultTags: Arb<List<String>> = Arb.list(Arb.string(2..10, Codepoint.az()), 0..3),
): Arb<MetadataTemplate.Other> = arbitrary {
    MetadataTemplate.Other(
        titleOverride = titleOverride.bind(),
        titlePattern = titlePattern.bind(),
        defaultTags = defaultTags.bind(),
    )
}

fun Arb.Companion.metadataTemplate(): Arb<MetadataTemplate> = arbitrary {
    when (Arb.int(0..2).bind()) {
        0 -> Arb.metadataTemplateMusicVideo().bind()
        1 -> Arb.metadataTemplateSeriesEpisode().bind()
        else -> Arb.metadataTemplateOther().bind()
    }
}

