package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.common.*
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.metadata.metadataTemplate
import io.github.alelk.tgvd.domain.storage.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Arb.Companion.rule(
    id: Arb<RuleId> = Arb.ruleId(),
    name: Arb<String> = Arb.string(3..25, Codepoint.az()),
    workspaceId: Arb<WorkspaceId> = Arb.workspaceId(),
    match: Arb<RuleMatch> = Arb.ruleMatch(maxDepth = 1),
    metadataTemplate: Arb<MetadataTemplate> = Arb.metadataTemplate(),
    downloadPolicy: Arb<DownloadPolicy> = Arb.downloadPolicy(),
    outputs: Arb<List<OutputRule>> = Arb.list(Arb.outputRule(), 1..3),
    enabled: Arb<Boolean> = Arb.boolean(),
    priority: Arb<Int> = Arb.int(0..20),
): Arb<Rule> = arbitrary {
    val now = Clock.System.now()
    Rule(
        id = id.bind(),
        name = name.bind(),
        workspaceId = workspaceId.bind(),
        match = match.bind(),
        metadataTemplate = metadataTemplate.bind(),
        downloadPolicy = downloadPolicy.bind(),
        outputs = outputs.bind(),
        enabled = enabled.bind(),
        priority = priority.bind(),
        createdAt = now,
        updatedAt = now,
    )
}
