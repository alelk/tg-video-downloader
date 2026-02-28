package io.github.alelk.tgvd.server.transport.route

import io.github.alelk.tgvd.api.contract.preview.PreviewRequestDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.rule.RuleSummaryDto
import io.github.alelk.tgvd.api.mapping.common.apiString
import io.github.alelk.tgvd.api.mapping.metadata.toDto
import io.github.alelk.tgvd.api.mapping.storage.toDto
import io.github.alelk.tgvd.api.mapping.video.toDto
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.metadata.category
import io.github.alelk.tgvd.domain.preview.PreviewUseCase
import io.github.alelk.tgvd.domain.storage.PathTemplateEngine
import io.github.alelk.tgvd.domain.video.VideoSource
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.server.request.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun Route.previewRoutes() {
    val previewUseCase by inject<PreviewUseCase>()
    val pathTemplateEngine by inject<PathTemplateEngine>()

    post<ApiV1.Preview> {
        val request = call.receive<PreviewRequestDto>()

        call.respondEither(previewUseCase.preview(request.url)) { result ->
            val context = pathTemplateEngine.buildContext(result.videoInfo, result.metadata)
            val storagePlan = pathTemplateEngine.buildStoragePlan(result.storagePolicy, context, result.videoInfo)

            PreviewResponseDto(
                source = VideoSource(
                    url = Url(request.url),
                    videoId = result.videoInfo.videoId,
                    extractor = result.videoInfo.extractor,
                ).toDto(),
                videoInfo = result.videoInfo.toDto(),
                matchedRule = result.matchedRule?.let {
                    RuleSummaryDto(id = it.id.value.toString(), name = it.name)
                },
                metadataSource = result.metadataSource.toDto(),
                category = result.metadata.category.apiString,
                metadata = result.metadata.toDto(),
                storagePlan = storagePlan.toDto(),
            )
        }
    }
}
