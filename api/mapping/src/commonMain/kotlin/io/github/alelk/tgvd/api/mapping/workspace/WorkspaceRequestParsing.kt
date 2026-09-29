package io.github.alelk.tgvd.api.mapping.workspace

import arrow.core.Either
import io.github.alelk.tgvd.api.mapping.common.parseValue
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.workspace.WorkspaceRole

/**
 * The `slug` of `POST /workspaces`. The route answers a bad slug with the legacy body
 * `{"error": <message>}` (not `ApiErrorDto`), so the message here is exactly the one the server has
 * always sent.
 */
fun parseNewWorkspaceSlug(raw: String): Either<DomainError.ValidationError, WorkspaceSlug> =
    parseValue("slug") { WorkspaceSlug(raw) }

/** A Telegram user id from the body or the path of the member endpoints. */
fun parseTelegramUserId(raw: Long): Either<DomainError.ValidationError, TelegramUserId> =
    parseValue("userId") { TelegramUserId(raw) }

/** The `role` of `POST …/members`: `"owner"` in any case is OWNER, anything else is MEMBER. */
fun parseWorkspaceRole(raw: String): WorkspaceRole =
    if (raw.lowercase() == "owner") WorkspaceRole.OWNER else WorkspaceRole.MEMBER
