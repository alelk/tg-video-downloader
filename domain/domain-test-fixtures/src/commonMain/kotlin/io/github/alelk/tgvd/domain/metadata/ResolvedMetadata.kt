package io.github.alelk.tgvd.domain.metadata

import io.github.alelk.tgvd.domain.common.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

fun Arb.Companion.resolvedMetadataMusicVideo(
    artist: Arb<String> = Arb.string(2..20, Codepoint.az()),
    title: Arb<String> = Arb.string(2..30, Codepoint.az()),
    releaseDate: Arb<LocalDate?> = Arb.domainLocalDate().orNull(0.4),
    tags: Arb<List<String>> = Arb.list(Arb.string(2..10, Codepoint.az()), 0..3),
    comment: Arb<String?> = Arb.string(5..50, Codepoint.az()).orNull(0.5),
): Arb<ResolvedMetadata.MusicVideo> = arbitrary {
    ResolvedMetadata.MusicVideo(
        artist = artist.bind(),
        title = title.bind(),
        releaseDate = releaseDate.bind(),
        tags = tags.bind(),
        comment = comment.bind(),
    )
}

fun Arb.Companion.resolvedMetadataSeriesEpisode(
    seriesName: Arb<String> = Arb.string(2..20, Codepoint.az()),
    season: Arb<String?> = Arb.element("01", "02", "03", "1", "2").orNull(0.3),
    episode: Arb<String?> = Arb.element("01", "02", "05", "10", "23").orNull(0.3),
    title: Arb<String> = Arb.string(2..30, Codepoint.az()),
    releaseDate: Arb<LocalDate?> = Arb.domainLocalDate().orNull(0.4),
    tags: Arb<List<String>> = Arb.list(Arb.string(2..10, Codepoint.az()), 0..3),
    comment: Arb<String?> = Arb.string(5..50, Codepoint.az()).orNull(0.5),
): Arb<ResolvedMetadata.SeriesEpisode> = arbitrary {
    ResolvedMetadata.SeriesEpisode(
        seriesName = seriesName.bind(),
        season = season.bind(),
        episode = episode.bind(),
        title = title.bind(),
        releaseDate = releaseDate.bind(),
        tags = tags.bind(),
        comment = comment.bind(),
    )
}

fun Arb.Companion.resolvedMetadataOther(
    title: Arb<String> = Arb.string(2..30, Codepoint.az()),
    releaseDate: Arb<LocalDate?> = Arb.domainLocalDate().orNull(0.4),
    tags: Arb<List<String>> = Arb.list(Arb.string(2..10, Codepoint.az()), 0..3),
    comment: Arb<String?> = Arb.string(5..50, Codepoint.az()).orNull(0.5),
): Arb<ResolvedMetadata.Other> = arbitrary {
    ResolvedMetadata.Other(
        title = title.bind(),
        releaseDate = releaseDate.bind(),
        tags = tags.bind(),
        comment = comment.bind(),
    )
}

fun Arb.Companion.resolvedMetadata(): Arb<ResolvedMetadata> = arbitrary {
    when (Arb.int(0..2).bind()) {
        0 -> Arb.resolvedMetadataMusicVideo().bind()
        1 -> Arb.resolvedMetadataSeriesEpisode().bind()
        else -> Arb.resolvedMetadataOther().bind()
    }
}




