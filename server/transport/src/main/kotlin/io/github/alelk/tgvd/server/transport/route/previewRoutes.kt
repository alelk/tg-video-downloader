package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.preview.DownloadHistoryEntryDto
import io.github.alelk.tgvd.api.contract.preview.PreviewRequestDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.rule.RuleSummaryDto
import io.github.alelk.tgvd.api.mapping.common.toDto as categoryToDto
import io.github.alelk.tgvd.api.mapping.metadata.metadataSourceToDto
import io.github.alelk.tgvd.api.mapping.metadata.toDto
import io.github.alelk.tgvd.api.mapping.preview.toDomain
import io.github.alelk.tgvd.api.mapping.preview.toDto
import io.github.alelk.tgvd.api.mapping.storage.toDto
import io.github.alelk.tgvd.api.mapping.video.toDto
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.job.JobRepository
import io.github.alelk.tgvd.domain.metadata.category
import io.github.alelk.tgvd.domain.preview.PreviewUseCase
import io.github.alelk.tgvd.domain.storage.PathTemplateEngine
import io.github.alelk.tgvd.domain.video.VideoSource
import io.github.alelk.tgvd.domain.workspace.WorkspaceRepository
import io.github.alelk.tgvd.server.transport.auth.parseWorkspaceSlug
import io.github.alelk.tgvd.server.transport.auth.telegramUser
import io.github.alelk.tgvd.server.transport.util.requireWorkspaceMember
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
    val workspaceRepository by inject<WorkspaceRepository>()
    val jobRepository by inject<JobRepository>()

    post<ApiV1.Workspaces.ById.Preview> { res ->
        val request = call.receive<PreviewRequestDto>()
        val user = call.telegramUser

        val result = either {
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            val overrides = request.overrides?.toDomain()
            val preview = previewUseCase(request.url, ws.id, overrides, force = request.force).bind()
            val context = pathTemplateEngine.buildContext(preview.videoInfo, preview.metadata)
            val storagePlan = pathTemplateEngine.buildStoragePlan(preview.outputs, context, preview.videoInfo)

            // Lookup terminal jobs for this video in the current workspace (history)
            val previousDownloads = jobRepository
                .findByVideoId(preview.videoInfo.videoId.value, ws.id)
                .filter { it.status.isTerminal }
                .map { job ->
                    DownloadHistoryEntryDto(
                        jobId = job.id.value.toString(),
                        status = job.status.name,
                        finishedAt = job.finishedAt?.toString(),
                        maxQuality = job.storagePlan.original.maxQuality?.toDto(),
                        formatSummary = job.storagePlan.original.format.extension,
                    )
                }

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
                metadataSource = metadataSourceToDto(preview.metadataSource),
                category = preview.metadata.category.categoryToDto(),
                metadata = preview.metadata.toDto(),
                storagePlan = storagePlan.toDto(),
                appliedOverrides = overrides?.toDto(),
                previousDownloads = previousDownloads,
            )
        }

        call.respondEither(result)
    }
}
