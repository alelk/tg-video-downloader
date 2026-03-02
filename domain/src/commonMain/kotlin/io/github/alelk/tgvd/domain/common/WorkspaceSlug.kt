package io.github.alelk.tgvd.domain.common

import kotlin.jvm.JvmInline

/**
 * Человекочитаемый уникальный идентификатор workspace.
 * Используется в URL path и конфигурации приложения.
 *
 * Требования:
 * - только строчные буквы, цифры и дефис
 * - от 3 до 50 символов
 * - начинается и заканчивается буквой или цифрой
 *
 * Примеры: "personal", "my-team", "project-alpha-2"
 */
@JvmInline
value class WorkspaceSlug(val value: String) {
    init {
        require(value.matches(SLUG_REGEX)) {
            "WorkspaceSlug must be 3–50 chars, lowercase letters/digits, hyphens allowed but not at start/end: '$value'"
        }
    }

    companion object {
        private val SLUG_REGEX = Regex("^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$")
    }

    override fun toString(): String = value
}

