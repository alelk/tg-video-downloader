package io.github.alelk.tgvd.domain.workspace

import io.github.alelk.tgvd.domain.common.WorkspaceId
import kotlin.time.Instant

/**
 * Workspace — группа пользователей с общими ресурсами.
 *
 * Все правила, задачи, настройки хранения принадлежат workspace, а не отдельному пользователю.
 * Пользователи одного workspace видят и управляют всеми его ресурсами.
 * Пользователь может состоять в нескольких workspaces и переключаться между ними.
 */
data class Workspace(
    val id: WorkspaceId,
    val name: String,
    val createdAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Workspace name cannot be blank" }
    }
}

