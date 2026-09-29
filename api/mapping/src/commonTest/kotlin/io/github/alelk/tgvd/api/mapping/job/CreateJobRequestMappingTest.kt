package io.github.alelk.tgvd.api.mapping.job

import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.job.CreateJobRequestDto
import io.github.alelk.tgvd.api.contract.job.SaveAsRuleDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataSourceDto
import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.api.contract.storage.MediaContainerDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.api.contract.video.MediaSelectionDto
import io.github.alelk.tgvd.api.contract.video.VideoFormatDto
import io.github.alelk.tgvd.api.contract.video.VideoInfoDto
import io.github.alelk.tgvd.api.contract.video.VideoSourceDto
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.VideoId
import io.github.alelk.tgvd.domain.fixtures.shouldBeLeft
import io.github.alelk.tgvd.domain.fixtures.shouldBeRight
import io.github.alelk.tgvd.domain.job.SaveAsRule
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CreateJobRequestMappingTest :
    FunSpec({
        val videoUrl = "https://example.com/watch?v=v1"
        val request =
            CreateJobRequestDto(
                source = VideoSourceDto(url = videoUrl, videoId = "v1", extractor = "youtube"),
                category = CategoryDto.OTHER,
                videoInfo =
                VideoInfoDto(
                    videoId = "v1",
                    extractor = "youtube",
                    title = "Title",
                    channelId = "UC1",
                    channelName = "Channel",
                    uploadDate = "2024-05-01",
                    durationSeconds = 60,
                    webpageUrl = videoUrl,
                    availableFormats = listOf(VideoFormatDto("140", "m4a", acodec = "mp4a", vcodec = "none")),
                ),
                metadata = ResolvedMetadataDto.Other(title = "Title"),
                metadataSource = MetadataSourceDto.FALLBACK,
                storagePlan =
                StoragePlanDto(
                    original = OutputTargetDto(
                        "/media/v1.mkv",
                        OutputFormatDto.OriginalVideo(MediaContainerDto.MKV),
                    ),
                    additional =
                    listOf(
                        OutputTargetDto("/media/v1.mp4", OutputFormatDto.ConvertedVideo(MediaContainerDto.MP4)),
                    ),
                ),
                mediaSelection = MediaSelectionDto(audioFormatIds = listOf("140"), subtitleLanguages = emptyList()),
            )

        fun validationError(dto: CreateJobRequestDto): DomainError.ValidationError =
            dto.toDomainRequest().shouldBeLeft().shouldBeInstanceOf<DomainError.ValidationError>()

        test("maps a well-formed request") {
            val ruleId = Uuid.random()
            val domain = request.copy(ruleId = ruleId.toString()).toDomainRequest().shouldBeRight()

            domain.source.videoId shouldBe VideoId("v1")
            domain.videoInfo.channelId.value shouldBe "UC1"
            domain.videoInfo.uploadDate?.value shouldBe "2024-05-01"
            domain.videoInfo.availableFormats.single().formatId shouldBe "140"
            domain.ruleId shouldBe RuleId(ruleId)
            domain.metadata shouldBe ResolvedMetadata.Other(title = "Title")
            domain.metadataSource shouldBe MetadataSource.FALLBACK
            domain.storagePlan.original.path shouldBe FilePath("/media/v1.mkv")
            domain.storagePlan.additional.single().path shouldBe FilePath("/media/v1.mp4")
            domain.mediaSelection shouldBe
                MediaSelection(audioFormatIds = listOf("140"), subtitleLanguages = emptyList())
            domain.saveAsRule shouldBe null
        }

        test("a broken ruleId is a ValidationError of ruleId (G10: was 500)") {
            validationError(request.copy(ruleId = "not-a-uuid")) shouldBe
                DomainError.ValidationError("ruleId", "Invalid ruleId: not-a-uuid")
        }

        test("a blank source.videoId or videoInfo.videoId keeps the error it had in the route") {
            validationError(request.copy(source = request.source.copy(videoId = " "))) shouldBe
                DomainError.ValidationError("source.videoId", "Cannot be blank")
            validationError(request.copy(videoInfo = request.videoInfo.copy(videoId = ""))) shouldBe
                DomainError.ValidationError("videoInfo.videoId", "Cannot be blank")
        }

        test("invalid metadata is reported first, as in the route") {
            validationError(request.copy(metadata = ResolvedMetadataDto.Other(title = " "), ruleId = "broken")) shouldBe
                DomainError.ValidationError("title", "Cannot be blank")
        }

        context("a value a domain type rejects is a ValidationError of its field (G10: was 500)") {
            withData(
                nameFn = { it.first },
                "source.url" to request.copy(source = request.source.copy(url = "ftp://x")),
                "source.extractor" to request.copy(source = request.source.copy(extractor = " ")),
                "videoInfo.channelId" to request.copy(videoInfo = request.videoInfo.copy(channelId = "")),
                "videoInfo.uploadDate" to request.copy(videoInfo = request.videoInfo.copy(uploadDate = "01.05.2024")),
                "videoInfo.webpageUrl" to request.copy(videoInfo = request.videoInfo.copy(webpageUrl = "example.com")),
                "storagePlan.original.path" to
                    request.copy(
                        storagePlan = request.storagePlan.copy(
                            original = request.storagePlan.original.copy(path = " "),
                        ),
                    ),
                "storagePlan.additional[0].path" to
                    request.copy(
                        storagePlan =
                        request.storagePlan.copy(
                            additional = listOf(request.storagePlan.additional[0].copy(path = "")),
                        ),
                    ),
            ) { (field, dto) ->
                validationError(dto).field shouldBe field
            }
        }

        test("saveAsRule options are carried over; includeCategory has no domain counterpart") {
            val dto = SaveAsRuleDto(enabled = false, matchBy = "channel_name", includeMetadataTemplate = false)
            request.copy(saveAsRule = dto).toDomainRequest().shouldBeRight().saveAsRule shouldBe
                SaveAsRule(
                    matchBy = SaveAsRule.MatchBy.CHANNEL_NAME,
                    includeMetadataTemplate = false,
                    includeStoragePolicy = true,
                    enabled = false,
                )
        }

        context("saveAsRule.matchBy is parsed case-insensitively; anything else means the channel id") {
            withData(
                nameFn = { "'${it.first}' -> ${it.second}" },
                "channelId" to SaveAsRule.MatchBy.CHANNEL_ID,
                "channel_id" to SaveAsRule.MatchBy.CHANNEL_ID,
                "CHANNELID" to SaveAsRule.MatchBy.CHANNEL_ID,
                "channelName" to SaveAsRule.MatchBy.CHANNEL_NAME,
                "channel_name" to SaveAsRule.MatchBy.CHANNEL_NAME,
                "Channel_Name" to SaveAsRule.MatchBy.CHANNEL_NAME,
                "tag" to SaveAsRule.MatchBy.CHANNEL_ID,
                "" to SaveAsRule.MatchBy.CHANNEL_ID,
            ) { (raw, expected) ->
                parseSaveAsRuleMatchBy(raw) shouldBe expected
            }
        }
    })
