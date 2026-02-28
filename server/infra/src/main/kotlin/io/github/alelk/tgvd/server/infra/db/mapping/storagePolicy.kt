package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.storage.OutputFormat
import io.github.alelk.tgvd.domain.storage.OutputTemplate
import io.github.alelk.tgvd.domain.storage.StoragePolicy
import io.github.alelk.tgvd.server.infra.db.model.OutputTemplatePm
import io.github.alelk.tgvd.server.infra.db.model.StoragePolicyPm

internal fun StoragePolicy.toPm(): StoragePolicyPm =
    StoragePolicyPm(
        originalTemplate = originalTemplate,
        additionalOutputs = additionalOutputs.map { it.toPm() },
    )

internal fun StoragePolicyPm.toDomain(): StoragePolicy =
    StoragePolicy(
        originalTemplate = originalTemplate,
        additionalOutputs = additionalOutputs.map { it.toDomain() },
    )

private fun OutputTemplate.toPm(): OutputTemplatePm =
    OutputTemplatePm(pathTemplate = pathTemplate, format = format.serialized)

private fun OutputTemplatePm.toDomain(): OutputTemplate =
    OutputTemplate(pathTemplate = pathTemplate, format = OutputFormat.parse(format))

