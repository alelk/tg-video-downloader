package io.github.alelk.tgvd.features.download.model

import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataSourceDto
import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.preview.UserOverridesDto
import io.github.alelk.tgvd.api.contract.storage.MediaContainerDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.api.contract.storage.VideoQualityDto
import io.github.alelk.tgvd.api.contract.video.VideoInfoDto
import io.github.alelk.tgvd.api.contract.video.VideoSourceDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe

class PreviewEditorValuesTest : FunSpec({
    fun preview(
        category: CategoryDto = CategoryDto.MUSIC_VIDEO,
        metadata: ResolvedMetadataDto = ResolvedMetadataDto.MusicVideo(
            artist = "Artist",
            title = "Title",
            album = "Album",
            tags = listOf("rock", "live"),
        ),
        original: OutputTargetDto = OutputTargetDto(
            path = "/music/original.webm",
            format = OutputFormatDto.OriginalVideo(MediaContainerDto.WEBM),
            maxQuality = VideoQualityDto.HD_1080,
            embedMetadata = true,
        ),
        additional: List<OutputTargetDto> = emptyList(),
    ) = PreviewResponseDto(
        source = VideoSourceDto("https://example.com/video", "video-1", "youtube"),
        videoInfo = VideoInfoDto(
            videoId = "video-1",
            extractor = "youtube",
            title = "Source title",
            channelId = "channel-1",
            channelName = "Channel",
            durationSeconds = 60,
            webpageUrl = "https://example.com/video",
        ),
        metadataSource = MetadataSourceDto.RULE,
        category = category,
        metadata = metadata,
        storagePlan = StoragePlanDto(original, additional),
    )

    test("creates editor values from music preview") {
        val values = PreviewEditorValues.from(preview())

        values.metadataType shouldBe CategoryDto.MUSIC_VIDEO
        values.artist shouldBe "Artist"
        values.album shouldBe "Album"
        values.tags shouldBe "rock, live"
        values.originalMaxQuality shouldBe VideoQualityDto.HD_1080
    }

    test("builds only explicitly edited overrides") {
        val values = PreviewEditorValues.from(preview()).copy(
            artist = "Edited artist",
            title = "Edited title",
        )

        values.toOverrides(setOf("artist")) shouldBe UserOverridesDto.MusicVideo(
            artist = "Edited artist",
        )
        values.toOverrides(emptySet()) shouldBe null
    }

    test("builds series metadata and normalizes optional fields and tags") {
        val values = PreviewEditorValues.from(preview()).copy(
            metadataType = CategoryDto.SERIES_EPISODE,
            seriesName = "Series",
            season = "",
            episode = "07",
            title = "Episode title",
            tags = " drama, , finale ",
        )

        values.toMetadata() shouldBe ResolvedMetadataDto.SeriesEpisode(
            seriesName = "Series",
            season = null,
            episode = "07",
            title = "Episode title",
            tags = listOf("drama", "finale"),
        )
    }

    test("storage plan retains non-editable original settings") {
        val original = preview().storagePlan.original
        val values = PreviewEditorValues.from(preview()).copy(
            originalPath = "/changed/video.mp4",
            originalFormat = OutputFormatDto.OriginalVideo(MediaContainerDto.MP4),
            originalMaxQuality = VideoQualityDto.HD_720,
        )

        val result = values.toStoragePlan(original).original

        result.path shouldBe "/changed/video.mp4"
        result.format shouldBe OutputFormatDto.OriginalVideo(MediaContainerDto.MP4)
        result.maxQuality shouldBe VideoQualityDto.HD_720
        result.embedMetadata shouldBe true
    }

    test("server refresh keeps edited fields and replaces server-owned fields") {
        val current = PreviewEditorValues.from(preview()).copy(
            artist = "My artist",
            title = "My title",
            originalPath = "/old/path.webm",
        )
        val response = preview(
            metadata = ResolvedMetadataDto.MusicVideo(
                artist = "Server artist",
                title = "Server title",
                album = "Server album",
            ),
            original = OutputTargetDto(
                path = "/new/path.mp4",
                format = OutputFormatDto.OriginalVideo(MediaContainerDto.MP4),
                maxQuality = VideoQualityDto.SD_480,
            ),
        )

        val merged = current.mergeServerResponse(response, setOf("artist", "title"))

        merged.artist shouldBe "My artist"
        merged.title shouldBe "My title"
        merged.album shouldBe "Server album"
        merged.originalPath shouldBe "/new/path.mp4"
        merged.originalMaxQuality shouldBe VideoQualityDto.SD_480
    }

    test("validation follows selected metadata type") {
        val music = PreviewEditorValues.from(preview()).copy(
            artist = "Bad/Artist",
            seriesName = "Bad/Series",
        )

        music.fieldErrors shouldContainKey "artist"
        music.fieldErrors shouldNotContainKey "seriesName"

        val series = music.copy(metadataType = CategoryDto.SERIES_EPISODE)
        series.fieldErrors shouldContainKey "seriesName"
        series.fieldErrors shouldNotContainKey "artist"
    }
})
