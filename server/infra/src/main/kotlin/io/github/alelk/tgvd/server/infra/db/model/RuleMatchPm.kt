package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface RuleMatchPm {

    @Serializable
    @SerialName("all-of")
    data class AllOf(val matches: List<RuleMatchPm>) : RuleMatchPm

    @Serializable
    @SerialName("any-of")
    data class AnyOf(val matches: List<RuleMatchPm>) : RuleMatchPm

    @Serializable
    @SerialName("channel-id")
    data class ChannelId(val value: String) : RuleMatchPm

    @Serializable
    @SerialName("channel-name")
    data class ChannelName(val value: String, val ignoreCase: Boolean = true) : RuleMatchPm

    @Serializable
    @SerialName("title-regex")
    data class TitleRegex(val pattern: String) : RuleMatchPm

    @Serializable
    @SerialName("url-regex")
    data class UrlRegex(val pattern: String) : RuleMatchPm

    @Serializable
    @SerialName("category-equals")
    data class CategoryEquals(val category: String) : RuleMatchPm
}

