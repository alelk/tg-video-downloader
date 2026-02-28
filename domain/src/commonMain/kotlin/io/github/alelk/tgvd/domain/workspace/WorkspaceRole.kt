package io.github.alelk.tgvd.domain.workspace

/**
 * Роль пользователя в workspace.
 *
 * - [OWNER] — создатель workspace, может управлять участниками
 * - [MEMBER] — участник, имеет доступ ко всем ресурсам workspace
 */
enum class WorkspaceRole {
    OWNER,
    MEMBER,
}

