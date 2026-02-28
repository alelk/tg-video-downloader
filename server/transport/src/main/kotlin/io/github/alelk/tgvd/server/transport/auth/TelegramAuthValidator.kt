package io.github.alelk.tgvd.server.transport.auth

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

class TelegramAuthValidator(
    private val botToken: String,
    private val devMode: Boolean = false,
    private val maxAgeSeconds: Long = 86400,
) {
    init {
        if (devMode) {
            logger.warn { "⚠️ TelegramAuthValidator running in DEV MODE - DO NOT USE IN PRODUCTION" }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun validate(initData: String): Either<AuthError, TelegramUser> {
        if (devMode && initData == "dev") {
            return TelegramUser(
                id = TelegramUserId(1),
                firstName = "Dev User",
                lastName = null,
                username = "dev",
            ).right()
        }

        val params = parseInitData(initData)
        val hash = params.remove("hash")
            ?: return AuthError.MissingHash.left()

        val authDate = params["auth_date"]?.toLongOrNull()
            ?: return AuthError.InvalidAuthDate.left()

        val age = System.currentTimeMillis() / 1000 - authDate
        if (age > maxAgeSeconds) {
            return AuthError.Expired.left()
        }

        val dataCheckString = params.entries
            .sortedBy { it.key }
            .joinToString("\n") { "${it.key}=${it.value}" }

        val secretKey = hmacSha256("WebAppData".toByteArray(), botToken.toByteArray())
        val expectedHash = hmacSha256(secretKey, dataCheckString.toByteArray()).toHexString()

        if (!java.security.MessageDigest.isEqual(hash.toByteArray(), expectedHash.toByteArray())) {
            return AuthError.InvalidHash.left()
        }

        val userJson = params["user"]
            ?: return AuthError.MissingUser.left()

        return try {
            val user = json.decodeFromString<TelegramUserDto>(userJson)
            TelegramUser(
                id = TelegramUserId(user.id),
                firstName = user.firstName,
                lastName = user.lastName,
                username = user.username,
            ).right()
        } catch (e: Exception) {
            AuthError.InvalidUser.left()
        }
    }

    private fun parseInitData(initData: String): MutableMap<String, String> {
        return initData.split("&")
            .filter { it.contains("=") }
            .associate {
                val (key, value) = it.split("=", limit = 2)
                key to URLDecoder.decode(value, StandardCharsets.UTF_8)
            }
            .toMutableMap()
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    sealed interface AuthError {
        data object MissingHash : AuthError
        data object InvalidHash : AuthError
        data object InvalidAuthDate : AuthError
        data object Expired : AuthError
        data object MissingUser : AuthError
        data object InvalidUser : AuthError
    }
}

@Serializable
private data class TelegramUserDto(
    val id: Long,
    @SerialName("first_name")
    val firstName: String,
    @SerialName("last_name")
    val lastName: String? = null,
    val username: String? = null,
    @SerialName("language_code")
    val languageCode: String? = null,
    @SerialName("is_premium")
    val isPremium: Boolean? = null,
)

