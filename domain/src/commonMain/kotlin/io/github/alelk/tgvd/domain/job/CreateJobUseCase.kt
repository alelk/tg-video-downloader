package io.github.alelk.tgvd.domain.job

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.rule.RuleMatch
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.domain.rule.toRule
import io.github.alelk.tgvd.domain.storage.validateStoragePaths
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess
import kotlin.time.Clock

/**
 * Outcome of [CreateJobUseCase].
 *
 * @param saveAsRuleError why the requested "save as rule" was not created; the job is created regardless.
 */
data class CreateJobResult(val job: Job, val saveAsRuleError: DomainError? = null)

/**
 * Creates a download job in the workspace [workspaceSlug] on behalf of [actor] and, when the request
 * asks for it, a rule reproducing the job ([CreateJobRequest.saveAsRule]).
 *
 * Everything runs in one read-write transaction and every check happens before the first write:
 * membership, `videoInfo.videoId` matching `source.videoId`, the media selection (audio tracks
 * and subtitle languages the video actually offers), the storage paths and "no other active job
 * for this video".
 *
 * A rule that cannot be built or that the repository refuses (a `Left`) does not fail the job: the
 * job is created and the refusal is returned in [CreateJobResult.saveAsRuleError]. An *exception* while saving the rule
 * (a database failure) rolls the whole transaction back — the job included. Before stage 01.6 the
 * rule was created in a separate transaction after the job had been committed.
 */
class CreateJobUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val jobRepository: JobRepository,
    private val ruleRepository: RuleRepository,
    private val txRunner: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
        request: CreateJobRequest,
    ): Either<DomainError, CreateJobResult> = txRunner.inRwTransaction {
        either {
            val workspace = workspaceAccess.requireMember(workspaceSlug, actor).bind()
            validate(request)

            // Checked within the transaction to avoid a check-then-insert race between two requests.
            val activeJobs = jobRepository.findActive().filter { it.source.videoId == request.source.videoId }
            ensure(activeJobs.isEmpty()) {
                DomainError.JobAlreadyExists(request.source.videoId, activeJobs.first().id)
            }

            val now = clock.now()
            val job = request.toJob(workspace.id, actor, now)
            val rule =
                request.saveAsRule?.let { saveAs ->
                    saveAs.matchBy.toRuleMatch(request.videoInfo).map { match ->
                        buildSaveAsRuleRequest(
                            workspaceId = workspace.id,
                            match = match,
                            job = job,
                            includeMetadataTemplate = saveAs.includeMetadataTemplate,
                            includeStoragePolicy = saveAs.includeStoragePolicy,
                            enabled = saveAs.enabled,
                        ).toRule(now)
                    }
                }

            val saved = jobRepository.save(job).bind()
            val ruleError = rule?.fold({ it }, { ruleRepository.save(it).leftOrNull() })
            CreateJobResult(saved, ruleError)
        }
    }

    private fun Raise<DomainError>.validate(request: CreateJobRequest) {
        ensure(request.source.videoId == request.videoInfo.videoId) {
            DomainError.ValidationError("videoInfo.videoId", "Must match source.videoId")
        }
        request.mediaSelection?.let { validateMediaSelection(it, request.videoInfo) }
        val plan = request.storagePlan
        val storagePaths =
            mapOf("storagePlan.original" to plan.original.path.value) +
                plan.additional.mapIndexed { i, target -> "storagePlan.additional[$i]" to target.path.value }
        validateStoragePaths(storagePaths).bind()
    }

    private fun Raise<DomainError>.validateMediaSelection(selection: MediaSelection, videoInfo: VideoInfo) {
        selection.audioFormatIds?.let { ids ->
            val audioOnlyIds = videoInfo.availableFormats.filter { it.isAudioOnly }.map { it.formatId }.toSet()
            ensure(ids.isNotEmpty() && ids.distinct().size == ids.size && ids.all { it in audioOnlyIds }) {
                DomainError.ValidationError("mediaSelection.audioFormatIds", "Select available audio tracks")
            }
        }
        selection.subtitleLanguages?.let { languages ->
            val available = videoInfo.subtitleTracks.map { it.language }.toSet()
            ensure(languages.distinct().size == languages.size && languages.all { it in available }) {
                DomainError.ValidationError("mediaSelection.subtitleLanguages", "Select available subtitle languages")
            }
        }
    }

    /** An audio track the user can pick: has an audio codec and no video stream. */
    private val VideoInfo.Format.isAudioOnly: Boolean
        get() = acodec != null && acodec != "none" && (vcodec == null || vcodec == "none")

    private fun SaveAsRule.MatchBy.toRuleMatch(videoInfo: VideoInfo): Either<DomainError, RuleMatch> = either {
        when (this@toRuleMatch) {
            SaveAsRule.MatchBy.CHANNEL_ID -> RuleMatch.ChannelId(videoInfo.channelId.value)
            SaveAsRule.MatchBy.CHANNEL_NAME -> {
                ensure(videoInfo.channelName.isNotBlank()) {
                    DomainError.ValidationError("saveAsRule.matchBy", "The video has no channel name to match on")
                }
                RuleMatch.ChannelName(videoInfo.channelName)
            }
        }
    }
}
