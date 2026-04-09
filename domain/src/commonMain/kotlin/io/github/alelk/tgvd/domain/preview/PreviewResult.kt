package io.github.alelk.tgvd.domain.preview

import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.rule.Rule
import io.github.alelk.tgvd.domain.storage.OutputRule
import io.github.alelk.tgvd.domain.video.VideoInfo

data class PreviewResult(
    val videoInfo: VideoInfo,
    val metadata: ResolvedMetadata,
    val metadataSource: MetadataSource,
    val matchedRule: Rule?,
    val outputs: List<OutputRule>,
)