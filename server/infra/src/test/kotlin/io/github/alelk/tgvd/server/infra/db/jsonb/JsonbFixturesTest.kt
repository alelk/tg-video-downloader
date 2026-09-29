@file:OptIn(ExperimentalUuidApi::class)

package io.github.alelk.tgvd.server.infra.db.jsonb

import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.job.Job
import io.github.alelk.tgvd.domain.job.JobStatus
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.workspace.WorkspaceRole
import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.alelk.tgvd.server.infra.db.ExposedTransactionRunner
import io.github.alelk.tgvd.server.infra.db.jsonb
import io.github.alelk.tgvd.server.infra.db.jsonb.fixtures.RawRows
import io.github.alelk.tgvd.server.infra.db.jsonb.fixtures.fixtureFilesOnDisk
import io.github.alelk.tgvd.server.infra.db.jsonb.fixtures.readFixture
import io.github.alelk.tgvd.server.infra.db.model.DownloadPolicyPm
import io.github.alelk.tgvd.server.infra.db.model.JobErrorPm
import io.github.alelk.tgvd.server.infra.db.model.JobProgressPm
import io.github.alelk.tgvd.server.infra.db.model.MediaSelectionPm
import io.github.alelk.tgvd.server.infra.db.model.MetadataTemplatePm
import io.github.alelk.tgvd.server.infra.db.model.OutputRulePm
import io.github.alelk.tgvd.server.infra.db.model.ResolvedMetadataPm
import io.github.alelk.tgvd.server.infra.db.model.RuleMatchPm
import io.github.alelk.tgvd.server.infra.db.model.StoragePlanPm
import io.github.alelk.tgvd.server.infra.db.model.TrackPreferencesPm
import io.github.alelk.tgvd.server.infra.db.model.VideoInfoPm
import io.github.alelk.tgvd.server.infra.db.repository.ChannelRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.JobRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.RuleRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.VideoInfoCacheImpl
import io.github.alelk.tgvd.server.infra.db.repository.WorkspaceRepositoryImpl
import io.github.alelk.tgvd.server.infra.service.SystemSettingsHolder
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.assertions.json.FieldComparison
import io.kotest.assertions.json.compareJsonOptions
import io.kotest.assertions.json.equalJson
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

/**
 * Frozen JSONB fixtures: every stored JSON shape (each `*Pm` written to a `jsonb` column, each
 * sealed variant, plus the JSON kept in `system_settings.value`) as the code of stage 01.4 wrote it.
 * Those rows already live in deployed databases, so the current code must keep reading them.
 *
 * For every file under `src/test/resources/jsonb-fixtures/<table>.<column>/<variant>.json`:
 * 1. **serializer**: it decodes with the current `*Pm` serializer and re-encodes to a JSON that still
 *    contains every original key with the original value (new keys may appear — additions are
 *    compatible; a renamed or removed key is not);
 * 2. **through the database**: a row inserted with raw SQL is read by the real repository and maps to
 *    the expected domain value from [JsonbFixtureCases].
 *
 * Fixtures are never regenerated; a new shape is a new file. To see it fail: rename any field of any
 * `*Pm` (or a `@SerialName`) — step 1 goes red for every fixture holding that key.
 */
class JsonbFixturesTest :
    FunSpec({
        val testDb = PostgresTestContainer.newMigratedDatabase()
        val db = testDb.database
        val tx = ExposedTransactionRunner(db)
        val connection = PostgresTestContainer.connect(testDb.config)
        val sql = RawRows(connection)
        val workspaceId = sql.insertWorkspace("jsonb-fixtures")

        val rules = RuleRepositoryImpl(db)
        val channels = ChannelRepositoryImpl(db)
        val jobs = JobRepositoryImpl(db)
        val cache = VideoInfoCacheImpl(db)

        suspend fun readRule(id: UUID) = tx.inRoTransaction {
            rules.findById(RuleId(id.toKotlinUuid()))
        }.shouldNotBeNull()

        suspend fun readChannel(id: UUID) =
            tx.inRoTransaction { channels.findById(ChannelDirectoryEntryId(id.toKotlinUuid())) }.shouldNotBeNull()

        suspend fun readJob(id: UUID): Job = tx.inRoTransaction {
            jobs.findById(JobId(id.toKotlinUuid()))
        }.shouldNotBeNull()

        val columns =
            listOf(
                FixtureColumn("rules.match", RuleMatchPm.serializer(), JsonbFixtureCases.ruleMatch) { json ->
                    readRule(sql.insertRule(workspaceId, match = json)).match
                },
                FixtureColumn(
                    "rules.metadata_template",
                    MetadataTemplatePm.serializer(),
                    JsonbFixtureCases.ruleMetadataTemplate,
                ) {
                    readRule(sql.insertRule(workspaceId, metadataTemplate = it)).metadataTemplate
                },
                FixtureColumn(
                    "rules.download_policy",
                    DownloadPolicyPm.serializer(),
                    JsonbFixtureCases.ruleDownloadPolicy,
                ) {
                    readRule(sql.insertRule(workspaceId, downloadPolicy = it)).downloadPolicy
                },
                FixtureColumn(
                    "rules.outputs",
                    ListSerializer(OutputRulePm.serializer()),
                    JsonbFixtureCases.ruleOutputs,
                ) {
                    readRule(sql.insertRule(workspaceId, outputs = it)).outputs
                },
                FixtureColumn(
                    "channels.metadata_overrides",
                    MetadataTemplatePm.serializer(),
                    JsonbFixtureCases.channelMetadataOverrides,
                ) { readChannel(sql.insertChannel(workspaceId, metadataOverrides = it)).metadataOverrides },
                FixtureColumn(
                    "channels.track_preferences",
                    TrackPreferencesPm.serializer(),
                    JsonbFixtureCases.channelTrackPreferences,
                ) { readChannel(sql.insertChannel(workspaceId, trackPreferences = it)).trackPreferences },
                FixtureColumn("jobs.raw_info", VideoInfoPm.serializer(), JsonbFixtureCases.jobRawInfo) {
                    readJob(sql.insertJob(workspaceId, rawInfo = it)).videoInfo
                },
                FixtureColumn("jobs.metadata", ResolvedMetadataPm.serializer(), JsonbFixtureCases.jobMetadata) {
                    readJob(sql.insertJob(workspaceId, metadata = it)).metadata
                },
                FixtureColumn("jobs.storage_plan", StoragePlanPm.serializer(), JsonbFixtureCases.jobStoragePlan) {
                    readJob(sql.insertJob(workspaceId, storagePlan = it)).storagePlan
                },
                FixtureColumn(
                    "jobs.media_selection",
                    MediaSelectionPm.serializer(),
                    JsonbFixtureCases.jobMediaSelection,
                ) {
                    readJob(sql.insertJob(workspaceId, mediaSelection = it)).mediaSelection
                },
                FixtureColumn("jobs.progress", JobProgressPm.serializer(), JsonbFixtureCases.jobProgress) { json ->
                    readJob(sql.insertJob(workspaceId, progress = json)).let { it.phase to it.progress }
                },
                FixtureColumn("jobs.error", JobErrorPm.serializer(), JsonbFixtureCases.jobError) {
                    readJob(sql.insertJob(workspaceId, error = it)).errorMessage
                },
                FixtureColumn(
                    "video_info_cache.video_info",
                    VideoInfoPm.serializer(),
                    JsonbFixtureCases.videoInfoCacheVideoInfo,
                ) {
                    val url = sql.insertVideoInfoCache(it)
                    tx.inRoTransaction { cache.get(url) }
                },
                FixtureColumn(
                    "system_settings.ytdlp",
                    YtDlpConfig.serializer(),
                    JsonbFixtureCases.systemSettingsYtDlp,
                ) {
                    sql.upsertSystemSetting("ytdlp", it)
                    // The config value differs from every fixture, so a silent fallback to it fails the comparison.
                    tx.inRwTransaction { SystemSettingsHolder(CONFIG_YT_DLP, CONFIG_PROXY, db) }.ytDlpConfig
                },
                FixtureColumn(
                    "system_settings.proxy",
                    ProxyConfig.serializer(),
                    JsonbFixtureCases.systemSettingsProxy,
                ) {
                    sql.upsertSystemSetting("proxy", it)
                    tx.inRwTransaction { SystemSettingsHolder(CONFIG_YT_DLP, CONFIG_PROXY, db) }.proxyConfig
                },
            )

        test("every fixture file has an expectation and every expectation has a file") {
            fixtureFilesOnDisk() shouldBe
                columns.flatMap { column -> column.cases.keys.map { "${column.directory}/$it.json" } }.toSet()
        }

        columns.forEach { column ->
            column.cases.forEach { (variant, expected) ->
                val path = "${column.directory}/$variant.json"

                test("$path decodes with the current serializer and keeps every stored key") {
                    val stored = readFixture(path)
                    column.reencode(stored) should equalJson(stored, KEEP_EVERY_STORED_KEY)
                }

                test("$path inserted with raw SQL is read by the repository") {
                    column.readBack(readFixture(path)) shouldBe expected
                }
            }
        }

        test("stored enum-like strings of existing rows are read (incl. pre-V3 legacy values)") {
            val statuses =
                mapOf(
                    "pending" to JobStatus.PENDING,
                    "downloading" to JobStatus.DOWNLOADING,
                    "post-processing" to JobStatus.POST_PROCESSING,
                    "completed" to JobStatus.COMPLETED,
                    "failed" to JobStatus.FAILED,
                    "cancelled" to JobStatus.CANCELLED,
                    "queued" to JobStatus.PENDING,
                    "running" to JobStatus.DOWNLOADING,
                    "done" to JobStatus.COMPLETED,
                )
            statuses.forEach { (stored, expected) ->
                withClue("jobs.status = '$stored'") {
                    readJob(sql.insertJob(workspaceId, status = stored)).status shouldBe
                        expected
                }
            }

            val sources =
                mapOf(
                    "rule" to MetadataSource.RULE,
                    "llm" to MetadataSource.LLM,
                    "fallback" to MetadataSource.FALLBACK,
                    "manual" to MetadataSource.FALLBACK,
                )
            sources.forEach { (stored, expected) ->
                withClue("jobs.metadata_source = '$stored'") {
                    readJob(sql.insertJob(workspaceId, metadataSource = stored)).metadataSource shouldBe expected
                }
            }

            val roles = mapOf("owner" to WorkspaceRole.OWNER, "member" to WorkspaceRole.MEMBER)
            val workspaces = WorkspaceRepositoryImpl(db)
            roles.forEach { (stored, expected) ->
                val userId = sql.insertMember(workspaceId, stored)
                withClue("workspace_members.role = '$stored'") {
                    tx
                        .inRoTransaction { workspaces.findMembers(WorkspaceId(workspaceId.toKotlinUuid())) }
                        .single { it.userId.value == userId }
                        .role shouldBe expected
                }
            }
        }

        afterSpec { connection.close() }
    })

private val KEEP_EVERY_STORED_KEY = compareJsonOptions { fieldComparison = FieldComparison.Lenient }

/** Config values that differ from every `system_settings` fixture. */
private val CONFIG_YT_DLP = YtDlpConfig(path = "/config/yt-dlp", retries = 1)
private val CONFIG_PROXY = ProxyConfig(host = "config-proxy", port = 1)

private class FixtureColumn(
    val directory: String,
    private val serializer: KSerializer<*>,
    val cases: Map<String, Any?>,
    val readBack: suspend (json: String) -> Any?,
) {
    @Suppress("UNCHECKED_CAST") // decode and encode use the same serializer instance
    fun reencode(stored: String): String {
        val typed = serializer as KSerializer<Any?>
        return jsonb.encodeToString(typed, jsonb.decodeFromString(typed, stored))
    }
}
