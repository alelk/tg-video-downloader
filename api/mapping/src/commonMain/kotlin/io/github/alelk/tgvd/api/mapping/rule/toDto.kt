package io.github.alelk.tgvd.api.mapping.rule

import io.github.alelk.tgvd.api.contract.rule.RuleDto
import io.github.alelk.tgvd.api.contract.rule.RuleMatchDto
import io.github.alelk.tgvd.api.mapping.common.apiString
import io.github.alelk.tgvd.api.mapping.metadata.toDto
import io.github.alelk.tgvd.api.mapping.storage.toDto
import io.github.alelk.tgvd.domain.metadata.category
import io.github.alelk.tgvd.domain.rule.Rule
import io.github.alelk.tgvd.domain.rule.RuleMatch
import kotlin.uuid.ExperimentalUuidApi

fun RuleMatch.toDto(): RuleMatchDto = when (this) {
    is RuleMatch.AllOf -> RuleMatchDto.AllOf(matches.map { it.toDto() })
    is RuleMatch.AnyOf -> RuleMatchDto.AnyOf(matches.map { it.toDto() })
    is RuleMatch.ChannelId -> RuleMatchDto.ChannelId(value)
    is RuleMatch.ChannelName -> RuleMatchDto.ChannelName(value, ignoreCase)
    is RuleMatch.TitleRegex -> RuleMatchDto.TitleRegex(pattern)
    is RuleMatch.UrlRegex -> RuleMatchDto.UrlRegex(pattern)
}

@OptIn(ExperimentalUuidApi::class)
fun Rule.toDto(): RuleDto = RuleDto(
    id = id.value.toString(),
    name = name,
    enabled = enabled,
    priority = priority,
    match = match.toDto(),
    category = metadataTemplate.category.apiString,
    metadataTemplate = metadataTemplate.toDto(),
    downloadPolicy = downloadPolicy.toDto(),
    storagePolicy = storagePolicy.toDto(),
    postProcessPolicy = postProcessPolicy.toDto(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

