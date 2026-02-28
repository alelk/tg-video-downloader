package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.storage.PostProcessPolicy
import io.github.alelk.tgvd.server.infra.db.model.PostProcessPolicyPm

internal fun PostProcessPolicy.toPm(): PostProcessPolicyPm =
    PostProcessPolicyPm(embedThumbnail, embedMetadata, normalizeAudio)

internal fun PostProcessPolicyPm.toDomain(): PostProcessPolicy =
    PostProcessPolicy(embedThumbnail, embedMetadata, normalizeAudio)

