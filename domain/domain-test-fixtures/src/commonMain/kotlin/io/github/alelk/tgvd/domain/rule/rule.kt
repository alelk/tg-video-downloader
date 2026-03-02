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
    storagePolicy: Arb<StoragePolicy> = Arb.storagePolicy(),
    downloadPolicy: Arb<DownloadPolicy> = Arb.downloadPolicy(),
    postProcessPolicy: Arb<PostProcessPolicy> = Arb.postProcessPolicy(),
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
        storagePolicy = storagePolicy.bind(),
        downloadPolicy = downloadPolicy.bind(),
        postProcessPolicy = postProcessPolicy.bind(),
        enabled = enabled.bind(),
        priority = priority.bind(),
        createdAt = now,
        updatedAt = now,
    )
}

