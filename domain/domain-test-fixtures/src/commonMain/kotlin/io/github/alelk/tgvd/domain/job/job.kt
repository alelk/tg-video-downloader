package io.github.alelk.tgvd.domain.job

import io.github.alelk.tgvd.domain.common.*
import io.github.alelk.tgvd.domain.metadata.*
import io.github.alelk.tgvd.domain.storage.*
import io.github.alelk.tgvd.domain.video.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Arb.Companion.job(
    id: Arb<JobId> = Arb.jobId(),
    workspaceId: Arb<WorkspaceId> = Arb.workspaceId(),
    createdBy: Arb<TelegramUserId> = Arb.telegramUserId(),
    source: Arb<VideoSource> = Arb.videoSource(),
    metadata: Arb<ResolvedMetadata> = Arb.resolvedMetadata(),
    metadataSource: Arb<MetadataSource> = Arb.metadataSource(),
    storagePlan: Arb<StoragePlan> = Arb.storagePlan(),
    ruleId: Arb<RuleId?> = Arb.ruleId().orNull(0.5),
    status: Arb<JobStatus> = Arb.jobStatus(),
    phase: Arb<JobPhase?> = Arb.jobPhase().orNull(0.4),
    progress: Arb<Int?> = Arb.int(0..100).orNull(0.5),
    errorMessage: Arb<String?> = Arb.string(5..50, Codepoint.az()).orNull(0.7),
): Arb<Job> = arbitrary {
    val now = Clock.System.now()
    Job(
        id = id.bind(),
        workspaceId = workspaceId.bind(),
        createdBy = createdBy.bind(),
        source = source.bind(),
        metadata = metadata.bind(),
        metadataSource = metadataSource.bind(),
        storagePlan = storagePlan.bind(),
        ruleId = ruleId.bind(),
        status = status.bind(),
        phase = phase.bind(),
        progress = progress.bind(),
        errorMessage = errorMessage.bind(),
        createdAt = now,
        updatedAt = now,
    )
}

