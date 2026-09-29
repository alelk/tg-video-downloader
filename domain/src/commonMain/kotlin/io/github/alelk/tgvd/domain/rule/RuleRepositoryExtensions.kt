package io.github.alelk.tgvd.domain.rule

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.WorkspaceId

/**
 * The rule [id] if it belongs to [workspaceId]. A rule of another workspace is reported exactly like a
 * missing one — [DomainError.RuleNotFound] — so its existence is not revealed.
 */
internal suspend fun RuleRepository.findInWorkspace(id: RuleId, workspaceId: WorkspaceId): Either<DomainError, Rule> =
    findById(id)?.takeIf { it.workspaceId == workspaceId }?.right() ?: DomainError.RuleNotFound(id).left()
