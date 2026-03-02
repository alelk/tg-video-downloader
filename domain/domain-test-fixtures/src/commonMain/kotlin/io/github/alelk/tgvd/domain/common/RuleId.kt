package io.github.alelk.tgvd.domain.common

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun Arb.Companion.ruleId(): Arb<RuleId> = arbitrary { RuleId(Uuid.random()) }

