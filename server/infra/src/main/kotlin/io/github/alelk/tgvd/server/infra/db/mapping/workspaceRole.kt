package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.workspace.WorkspaceRole

internal fun WorkspaceRole.toDbString(): String = name.lowercase()

internal fun String.toWorkspaceRole(): WorkspaceRole = when (this) {
    "owner" -> WorkspaceRole.OWNER
    "member" -> WorkspaceRole.MEMBER
    else -> error("Unknown workspace role: $this")
}

