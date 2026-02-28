package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.preview.PreviewRequestDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.rule.RuleSummaryDto
import io.github.alelk.tgvd.api.mapping.common.apiString
import io.github.alelk.tgvd.api.mapping.metadata.toDto
import io.github.alelk.tgvd.api.mapping.storage.toDto
import io.github.alelk.tgvd.api.mapping.video.toDto
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.metadata.category
import io.github.alelk.tgvd.domain.preview.PreviewUseCase
import io.github.alelk.tgvd.domain.storage.PathTemplateEngine
import io.github.alelk.tgvd.domain.video.VideoSource
import io.github.alelk.tgvd.server.transport.auth.parseWorkspaceId
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

    post<ApiV1.Workspaces.ById.Preview> { res ->
        val request = call.receive<PreviewRequestDto>()

        val result = either<DomainError, PreviewResponseDto> {
            val wsId = parseWorkspaceId(res.parent.workspaceId).bind()
            val preview = previewUseCase.preview(request.url, wsId).bind()
            val context = pathTemplateEngine.buildContext(preview.videoInfo, preview.metadata)
            val storagePlan = pathTemplateEngine.buildStoragePlan(preview.storagePolicy, context, preview.videoInfo)

            PreviewResponseDto(
                source = VideoSource(
                    url = Url(request.url),
                    videoId = preview.videoInfo.videoId,
                    extractor = preview.videoInfo.extractor,
                ).toDto(),
                videoInfo = preview.videoInfo.toDto(),
                matchedRule = preview.matchedRule?.let {
                    RuleSummaryDto(id = it.id.value.toString(), name = it.name)
                },
                metadataSource = preview.metadataSource.toDto(),
                category = preview.metadata.category.apiString,
                metadata = preview.metadata.toDto(),
                storagePlan = storagePlan.toDto(),
            )
        }

        call.respondEither(result)
    }
}
