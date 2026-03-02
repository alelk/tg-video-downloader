package io.github.alelk.tgvd.domain.job

import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum

fun Arb.Companion.jobPhase(): Arb<JobPhase> = Arb.enum<JobPhase>()