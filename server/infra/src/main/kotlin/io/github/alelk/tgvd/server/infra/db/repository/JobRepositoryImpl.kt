package io.github.alelk.tgvd.server.infra.db.repository

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.*
import io.github.alelk.tgvd.domain.job.*
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.video.VideoSource
import io.github.alelk.tgvd.server.infra.db.dbQuery
import io.github.alelk.tgvd.server.infra.db.mapping.*
import io.github.alelk.tgvd.server.infra.db.model.JobErrorPm
import io.github.alelk.tgvd.server.infra.db.model.JobProgressPm
import io.github.alelk.tgvd.server.infra.db.table.JobsTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalUuidApi::class)
class JobRepositoryImpl(
    private val database: Database,
) : JobRepository {

    override suspend fun findById(id: JobId): Job? = dbQuery(database) {
        JobsTable.selectAll()
            .where { JobsTable.id eq id.value }
            .singleOrNull()
            ?.toJob()
    }

    override suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Job> = dbQuery(database) {
        JobsTable.selectAll()
            .where { JobsTable.workspaceId eq workspaceId.value }
            .orderBy(JobsTable.createdAt, SortOrder.DESC)
            .map { it.toJob() }
    }

    override suspend fun findActive(): List<Job> = dbQuery(database) {
        JobsTable.selectAll()
            .where { JobsTable.status inList listOf("queued", "running", "post-processing") }
            .orderBy(JobsTable.createdAt, SortOrder.ASC)
            .map { it.toJob() }
    }

    override suspend fun save(job: Job): Either<DomainError, Job> = dbQuery(database) {
        val exists = JobsTable.selectAll()
            .where { JobsTable.id eq job.id.value }
            .count() > 0

        if (exists) {
            JobsTable.update({ JobsTable.id eq job.id.value }) {
                it[workspaceId] = job.workspaceId.value
                it[status] = job.status.toDbString()
                it[videoId] = job.source.videoId.value
                it[sourceUrl] = job.source.url.value
                it[sourceExtractor] = job.source.extractor.value
                it[ruleId] = job.ruleId?.value
                it[category] = job.metadata.categoryDbString()
                it[rawInfo] = job.source.toVideoInfoPm(job.metadata)
                it[metadata] = job.metadata.toPm()
                it[storagePlan] = job.storagePlan.toPm()
                it[progress] = job.phase?.let { phase ->
                    JobProgressPm(phase = phase.toDbString(), percent = job.progress ?: 0)
                }
                it[JobsTable.error] = job.errorMessage?.let { msg ->
                    JobErrorPm(code = "ERROR", message = msg)
                }
                it[attempt] = 1
                it[createdByTelegramUserId] = job.createdBy.value
                it[updatedAt] = job.updatedAt
            }
        } else {
            JobsTable.insert {
                it[id] = job.id.value
                it[workspaceId] = job.workspaceId.value
                it[status] = job.status.toDbString()
                it[videoId] = job.source.videoId.value
                it[sourceUrl] = job.source.url.value
                it[sourceExtractor] = job.source.extractor.value
                it[ruleId] = job.ruleId?.value
                it[category] = job.metadata.categoryDbString()
                it[rawInfo] = job.source.toVideoInfoPm(job.metadata)
                it[metadata] = job.metadata.toPm()
                it[storagePlan] = job.storagePlan.toPm()
                it[progress] = null
                it[JobsTable.error] = null
                it[attempt] = 1
                it[createdByTelegramUserId] = job.createdBy.value
            }
        }
        job.right()
    }

    override suspend fun updateStatus(
        id: JobId,
        status: JobStatus,
        phase: JobPhase?,
        progress: Int?,
        errorMessage: String?,
    ): Either<DomainError, Job> = dbQuery(database) {
        val timestamp = now()
        JobsTable.update({ JobsTable.id eq id.value }) {
            it[JobsTable.status] = status.toDbString()
            it[JobsTable.progress] = phase?.let { p ->
                JobProgressPm(phase = p.toDbString(), percent = progress ?: 0)
            }
            if (errorMessage != null) {
                it[JobsTable.error] = JobErrorPm(code = "ERROR", message = errorMessage, retryable = false)
            }
            it[updatedAt] = timestamp
            if (status == JobStatus.DOWNLOADING || status == JobStatus.POST_PROCESSING) {
                it[startedAt] = timestamp
            }
            if (status.isTerminal) {
                it[finishedAt] = timestamp
            }
        }
        findById(id)?.right() ?: DomainError.JobNotFound(id).left()
    }

    private fun ResultRow.toJob(): Job = Job(
        id = JobId(this[JobsTable.id].value),
        workspaceId = WorkspaceId(this[JobsTable.workspaceId].value),
        createdBy = TelegramUserId(this[JobsTable.createdByTelegramUserId]),
        source = VideoSource(
            url = Url(this[JobsTable.sourceUrl]),
            videoId = VideoId(this[JobsTable.videoId]),
            extractor = Extractor(this[JobsTable.sourceExtractor]),
        ),
        metadata = this[JobsTable.metadata].toDomain(),
        metadataSource = MetadataSource.RULE,
        storagePlan = this[JobsTable.storagePlan].toDomain(),
        ruleId = this[JobsTable.ruleId]?.value?.let { RuleId(it) },
        status = this[JobsTable.status].toJobStatus(),
        phase = this[JobsTable.progress]?.phase?.toJobPhase(),
        progress = this[JobsTable.progress]?.percent,
        errorMessage = this[JobsTable.error]?.message,
        createdAt = this[JobsTable.createdAt],
        updatedAt = this[JobsTable.updatedAt],
    )
}
