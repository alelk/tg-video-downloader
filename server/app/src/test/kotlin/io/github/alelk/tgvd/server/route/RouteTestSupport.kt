package io.github.alelk.tgvd.server.route

import io.github.alelk.tgvd.api.contract.common.apiJson
import io.github.alelk.tgvd.domain.system.YtDlpService
import io.github.alelk.tgvd.domain.video.VideoDownloader
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor
import io.github.alelk.tgvd.server.fakes.FakeVideoDownloader
import io.github.alelk.tgvd.server.fakes.FakeVideoInfoExtractor
import io.github.alelk.tgvd.server.fakes.FakeYtDlpService
import io.github.alelk.tgvd.server.infra.config.AppConfig
import io.github.alelk.tgvd.server.infra.config.DbConfig
import io.github.alelk.tgvd.server.infra.config.ServerConfig
import io.github.alelk.tgvd.server.infra.config.StorageConfig
import io.github.alelk.tgvd.server.infra.config.TelegramConfig
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.TestApplication
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.koin.dsl.module
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import io.github.alelk.tgvd.server.module as serverModule

const val TEST_BOT_TOKEN = "123456:route-test-bot-token"

/** The Telegram user id of `X-Telegram-Init-Data: dev` in dev mode (`TelegramAuthValidator`). */
const val DEV_USER_ID = 1L

/** Header name the Mini App sends on every request. */
const val INIT_DATA_HEADER = "X-Telegram-Init-Data"

/** A config for tests: dev mode on, the given database, everything else at its default. */
fun testConfig(db: DbConfig): AppConfig = AppConfig(
    server = ServerConfig(),
    telegram = TelegramConfig(botToken = TEST_BOT_TOKEN, devMode = true),
    db = db,
    storage = StorageConfig(
        baseDirectories = listOf("/tmp/tgvd-test/media"),
        tempDirectory = "/tmp/tgvd-test/temp",
    ),
)

/**
 * The whole server (`module()`) over a fresh per-spec PostgreSQL database, background services off,
 * yt-dlp adapters replaced by fakes. Started in `beforeSpec`, stopped in `afterSpec`; one instance per
 * spec (Koin is global in the Ktor plugin, so two must never run at once).
 */
class RouteTestApp {
    val extractor = FakeVideoInfoExtractor()
    val ytDlp = FakeYtDlpService()
    lateinit var dbConfig: DbConfig
        private set
    private lateinit var app: TestApplication
    lateinit var client: HttpClient
        private set

    suspend fun start() {
        dbConfig = PostgresTestContainer.newDatabaseConfig()
        val fakes =
            module {
                single<VideoInfoExtractor> { extractor }
                single<VideoDownloader> { FakeVideoDownloader() }
                single<YtDlpService> { ytDlp }
            }
        app =
            TestApplication {
                application {
                    serverModule(testConfig(dbConfig), startBackgroundServices = false, overrides = listOf(fakes))
                }
            }
        app.start()
        client = app.createClient { install(ContentNegotiation) { json(apiJson) } }
    }

    suspend fun stop() {
        app.stop()
    }

    /** Makes the fake yt-dlp know [videoId] (at `videoUrl(videoId)`). */
    fun knowsVideo(videoId: String, channelId: String = "UC-route-tests") {
        extractor.videos[videoUrl(videoId)] = aVideoInfo(videoId, channelId)
    }
}

/** Registers a [RouteTestApp] on the spec's lifecycle. */
fun FunSpec.routeTestApp(): RouteTestApp {
    val app = RouteTestApp()
    beforeSpec { app.start() }
    afterSpec { app.stop() }
    return app
}

/** Authenticates as the dev user (`devMode = true`, id [DEV_USER_ID]). */
fun HttpRequestBuilder.asDevUser() {
    header(INIT_DATA_HEADER, "dev")
}

/** Authenticates as another Telegram user with a correctly signed `initData` for [TEST_BOT_TOKEN]. */
fun HttpRequestBuilder.asTelegramUser(userId: Long, username: String = "user$userId") {
    header(INIT_DATA_HEADER, signedInitData(userId, username))
}

fun HttpRequestBuilder.jsonBody(body: String) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

inline fun <reified T> HttpRequestBuilder.jsonBody(value: T) {
    contentType(ContentType.Application.Json)
    setBody(value)
}

suspend fun <T> HttpResponse.decode(serializer: KSerializer<T>): T = apiJson.decodeFromString(serializer, bodyAsText())

suspend fun HttpResponse.jsonObject(): JsonObject = apiJson.parseToJsonElement(bodyAsText()).jsonObject

/**
 * Telegram Mini App `initData` signed as Telegram does: HMAC-SHA256 over the sorted
 * `key=value` lines with the key `HMAC-SHA256("WebAppData", botToken)`.
 */
fun signedInitData(userId: Long, username: String, botToken: String = TEST_BOT_TOKEN): String {
    val params =
        sortedMapOf(
            "auth_date" to (System.currentTimeMillis() / 1000).toString(),
            "query_id" to "AAH-route-test",
            "user" to """{"id":$userId,"first_name":"User $userId","username":"$username"}""",
        )
    val dataCheckString = params.entries.joinToString("\n") { "${it.key}=${it.value}" }
    val secret = hmacSha256("WebAppData".toByteArray(), botToken.toByteArray())
    val hash = hmacSha256(secret, dataCheckString.toByteArray()).joinToString("") { "%02x".format(it) }
    return (params + ("hash" to hash)).entries.joinToString("&") { (key, value) ->
        "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
    }
}

private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
    init(SecretKeySpec(key, "HmacSHA256"))
    doFinal(data)
}
