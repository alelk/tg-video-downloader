package io.github.alelk.tgvd.server.transport.auth

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.WorkspaceSlug

/**
 * Parses `workspaceSlug` string (from Ktor Resource path parameter) into [WorkspaceSlug].
 * Returns [Either.Left] with [DomainError.ValidationError] if the slug format is invalid.
 */
fun parseWorkspaceSlug(raw: String): Either<DomainError, WorkspaceSlug> =
    runCatching { WorkspaceSlug(raw).right() }
        .getOrElse { DomainError.ValidationError("workspaceSlug", it.message ?: "Invalid workspace slug: $raw").left() }
