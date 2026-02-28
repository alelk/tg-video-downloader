package io.github.alelk.tgvd.server.transport.auth

import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.server.transport.util.parseId
import kotlin.uuid.ExperimentalUuidApi

/**
 * Parses `workspaceId` string (from Ktor Resource path parameter) into [WorkspaceId].
 * Returns [arrow.core.Either.Left] with [io.github.alelk.tgvd.domain.common.DomainError.ValidationError] on invalid UUID.
 */
@OptIn(ExperimentalUuidApi::class)
fun parseWorkspaceId(raw: String) = parseId(raw, "workspaceId", ::WorkspaceId)
