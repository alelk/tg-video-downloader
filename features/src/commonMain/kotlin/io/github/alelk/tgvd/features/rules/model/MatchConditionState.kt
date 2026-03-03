package io.github.alelk.tgvd.features.rules.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.github.alelk.tgvd.api.contract.rule.RuleMatchDto

/** Mutable UI state model for editing match conditions (including recursive AllOf/AnyOf). */
sealed class MatchConditionState {

    data class Simple(
        var type: String = "channel-name",
        var value: String = "",
        var ignoreCase: Boolean = true,
    ) : MatchConditionState()

    data class Composite(
        var operator: CompositeOperator = CompositeOperator.ALL_OF,
        val children: SnapshotStateList<MatchConditionState> = mutableStateListOf(),
    ) : MatchConditionState()
}

enum class CompositeOperator { ALL_OF, ANY_OF }

/** Convert DTO → mutable UI state */
fun RuleMatchDto.toState(): MatchConditionState = when (this) {
    is RuleMatchDto.ChannelId -> MatchConditionState.Simple("channel-id", value)
    is RuleMatchDto.ChannelName -> MatchConditionState.Simple("channel-name", value, ignoreCase)
    is RuleMatchDto.TitleRegex -> MatchConditionState.Simple("title-regex", pattern)
    is RuleMatchDto.UrlRegex -> MatchConditionState.Simple("url-regex", pattern)
    is RuleMatchDto.AllOf -> MatchConditionState.Composite(
        operator = CompositeOperator.ALL_OF,
        children = mutableStateListOf<MatchConditionState>().also { list -> matches.forEach { list.add(it.toState()) } },
    )
    is RuleMatchDto.AnyOf -> MatchConditionState.Composite(
        operator = CompositeOperator.ANY_OF,
        children = mutableStateListOf<MatchConditionState>().also { list -> matches.forEach { list.add(it.toState()) } },
    )
}

/** Convert mutable UI state → DTO */
fun MatchConditionState.toDto(): RuleMatchDto = when (this) {
    is MatchConditionState.Simple -> when (type) {
        "channel-id" -> RuleMatchDto.ChannelId(value)
        "channel-name" -> RuleMatchDto.ChannelName(value, ignoreCase)
        "title-regex" -> RuleMatchDto.TitleRegex(value)
        "url-regex" -> RuleMatchDto.UrlRegex(value)
        else -> RuleMatchDto.ChannelName(value, ignoreCase)
    }
    is MatchConditionState.Composite -> when (operator) {
        CompositeOperator.ALL_OF -> RuleMatchDto.AllOf(children.map { it.toDto() })
        CompositeOperator.ANY_OF -> RuleMatchDto.AnyOf(children.map { it.toDto() })
    }
}

/** Recursively validate: all leaf Simple nodes must have non-blank values */
fun MatchConditionState.isValid(): Boolean = when (this) {
    is MatchConditionState.Simple -> value.isNotBlank()
    is MatchConditionState.Composite -> children.isNotEmpty() && children.all { it.isValid() }
}

