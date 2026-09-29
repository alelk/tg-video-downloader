package io.github.alelk.tgvd.domain.preview

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.channel.ChannelRepository
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.job.JobRepository
import io.github.alelk.tgvd.domain.storage.PathTemplateEngine
import io.github.alelk.tgvd.domain.storage.effectiveDownloadPolicy
import io.github.alelk.tgvd.domain.track.TrackSelectionSettingsProvider
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.video.VideoSource
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess

/**
 * The preview of a URL in the workspace [workspaceSlug] for [actor]: [PreviewUseCase] (video info,
 * rule, metadata, outputs) plus what the preview screen needs around it — the rendered storage plan,
 * the pre-selected tracks under the effective (rule + channel) download policy, and the earlier
 * finished downloads of the same video in this workspace.
 *
 * The membership check and the lookups run in read-only transactions; the video-info extraction
 * (yt-dlp) inside [PreviewUseCase] runs outside any transaction.
 */
@Suppress("LongParameterList") // one port per source the preview screen reads; a facade would only hide them
class PreviewVideoUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val previewUseCase: PreviewUseCase,
    private val pathTemplateEngine: PathTemplateEngine,
    private val channelRepository: ChannelRepository,
    private val jobRepository: JobRepository,
    private val trackSelectionSettings: TrackSelectionSettingsProvider,
    private val txRunner: TransactionRunner,
) {
    /**
     * @param url the URL as the user entered it.
     * @param overrides the user's metadata overrides; null = none.
     * @param force bypass the video-info cache (see [PreviewUseCase]).
     */
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
        url: String,
        overrides: UserOverrides?,
        force: Boolean,
    ): Either<DomainError, VideoPreview> = either {
        val workspace = txRunner.inRoTransaction { workspaceAccess.requireMember(workspaceSlug, actor) }.bind()
        val preview = previewUseCase(url, workspace.id, overrides, force = force).bind()
        val video = preview.videoInfo
        val context = pathTemplateEngine.buildContext(video, preview.metadata)
        val storagePlan = pathTemplateEngine.buildStoragePlan(preview.outputs, context, video)
        val (channel, history) =
            txRunner.inRoTransaction {
                channelRepository.findByChannelId(workspace.id, video.channelId, video.extractor) to
                    jobRepository.findByVideoId(video.videoId.value, workspace.id).filter { it.status.isTerminal }
            }
        val policy = effectiveDownloadPolicy(preview.matchedRule, channel)
        VideoPreview(
            source = VideoSource(url = Url(url), videoId = video.videoId, extractor = video.extractor),
            result = preview,
            storagePlan = storagePlan,
            appliedOverrides = overrides,
            previousDownloads = history,
            defaultMediaSelection =
            defaultMediaSelection(video, policy, trackSelectionSettings.trackSelectionSettings()),
        )
    }
}
