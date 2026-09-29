package io.github.alelk.tgvd.server.infra.db.jsonb.fixtures

import java.io.File
import java.sql.Connection
import java.util.UUID

private const val FIXTURE_ROOT = "jsonb-fixtures"

/** A frozen fixture file, `path` relative to `src/test/resources/jsonb-fixtures/`. */
internal fun readFixture(path: String): String =
    checkNotNull(RawRows::class.java.classLoader.getResource("$FIXTURE_ROOT/$path")) {
        "missing fixture $path"
    }
        .readText()

/** Every fixture file on disk, as `<table>.<column>/<variant>.json`. */
internal fun fixtureFilesOnDisk(): Set<String> {
    val root = File(checkNotNull(RawRows::class.java.classLoader.getResource(FIXTURE_ROOT)).toURI())
    return root
        .walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(root).invariantSeparatorsPath }
        .toSet()
}

/**
 * Rows written with plain SQL, the way an older server version left them. Columns that are not under
 * test get the first fixture of their own column (itself verified by this spec).
 */
internal class RawRows(private val connection: Connection) {
    private var sequence = 0

    fun insertWorkspace(slug: String): UUID = UUID.randomUUID().also { id ->
        execute("INSERT INTO workspaces (id, name, slug) VALUES (?, ?, ?)", id, "Fixtures", slug)
    }

    fun insertMember(workspaceId: UUID, role: String): Long = (1_000L + next()).also { userId ->
        execute(
            "INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, ?)",
            workspaceId,
            userId,
            role,
        )
    }

    fun insertRule(
        workspaceId: UUID,
        match: String = readFixture("rules.match/channel-id.json"),
        metadataTemplate: String = readFixture("rules.metadata_template/other.json"),
        downloadPolicy: String = readFixture("rules.download_policy/defaults.json"),
        outputs: String = readFixture("rules.outputs/single-default-encode.json"),
    ): UUID = UUID.randomUUID().also { id ->
        execute(
            """
                INSERT INTO rules (id, workspace_id, name, match, category, metadata_template, download_policy, outputs)
                VALUES (?, ?, ?, ?::jsonb, 'other', ?::jsonb, ?::jsonb, ?::jsonb)
            """.trimIndent(),
            id,
            workspaceId,
            "fixture rule ${next()}",
            match,
            metadataTemplate,
            downloadPolicy,
            outputs,
        )
    }

    fun insertChannel(workspaceId: UUID, metadataOverrides: String? = null, trackPreferences: String? = null): UUID =
        UUID.randomUUID().also { id ->
            execute(
                """
                INSERT INTO channels (id, workspace_id, channel_id, extractor, name, tags, metadata_overrides, track_preferences)
                VALUES (?, ?, ?, 'youtube', 'Fixture channel', '{fixtures}', ?::jsonb, ?::jsonb)
                """.trimIndent(),
                id,
                workspaceId,
                "UC-fixture-${next()}",
                metadataOverrides,
                trackPreferences,
            )
        }

    fun insertJob(
        workspaceId: UUID,
        status: String = "completed",
        metadataSource: String = "rule",
        rawInfo: String = readFixture("jobs.raw_info/minimal.json"),
        metadata: String = readFixture("jobs.metadata/other-minimal.json"),
        storagePlan: String = readFixture("jobs.storage_plan/original-only.json"),
        mediaSelection: String? = null,
        progress: String? = null,
        error: String? = null,
    ): UUID = UUID.randomUUID().also { id ->
        execute(
            """
                INSERT INTO jobs (id, workspace_id, status, video_id, source_url, source_extractor, category, raw_info,
                                  metadata, storage_plan, media_selection, progress, error, metadata_source, attempt,
                                  created_by_telegram_user_id)
                VALUES (?, ?, ?, ?, 'https://rutube.ru/video/fixture/', 'rutube', 'other', ?::jsonb,
                        ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, 1, 42)
            """.trimIndent(),
            id,
            workspaceId,
            status,
            "fixture-${next()}",
            rawInfo,
            metadata,
            storagePlan,
            mediaSelection,
            progress,
            error,
            metadataSource,
        )
    }

    fun insertVideoInfoCache(videoInfo: String): String = "https://example.com/fixture/${next()}".also { url ->
        execute(
            "INSERT INTO video_info_cache (url, video_info, expires_at) VALUES (?, ?::jsonb, NULL)",
            url,
            videoInfo,
        )
    }

    fun upsertSystemSetting(key: String, value: String) = execute(
        "INSERT INTO system_settings (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value",
        key,
        value,
    )

    private fun next(): Int = ++sequence

    private fun execute(statement: String, vararg parameters: Any?) {
        connection.prepareStatement(statement).use { prepared ->
            parameters.forEachIndexed { index, value -> prepared.setObject(index + 1, value) }
            prepared.executeUpdate()
        }
    }
}
