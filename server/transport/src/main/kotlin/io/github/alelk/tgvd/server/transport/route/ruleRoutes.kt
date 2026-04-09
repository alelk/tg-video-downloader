package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.rule.CreateRuleRequestDto
import io.github.alelk.tgvd.api.contract.rule.RuleDto
import io.github.alelk.tgvd.api.contract.rule.RuleListResponseDto
import io.github.alelk.tgvd.api.mapping.rule.toDomainRequest
import io.github.alelk.tgvd.api.mapping.rule.toDto
import io.github.alelk.tgvd.api.mapping.rule.toUpdateDomain
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.rule.*
import io.github.alelk.tgvd.domain.workspace.WorkspaceRepository
import io.github.alelk.tgvd.server.transport.auth.parseWorkspaceSlug
import io.github.alelk.tgvd.server.transport.auth.telegramUser
import io.github.alelk.tgvd.server.transport.util.parseId
import io.github.alelk.tgvd.server.transport.util.requireWorkspaceMember
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.delete
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun Route.ruleRoutes() {
    val createRule by inject<CreateRuleUseCase>()
    val updateRule by inject<UpdateRuleUseCase>()
    val deleteRule by inject<DeleteRuleUseCase>()
    val ruleRepository by inject<RuleRepository>()
    val workspaceRepository by inject<WorkspaceRepository>()

    get<ApiV1.Workspaces.ById.Rules> { res ->
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            RuleListResponseDto(items = ruleRepository.findByWorkspace(ws.id).map { it.toDto() })
        }
        call.respondEither(result)
    }

    post<ApiV1.Workspaces.ById.Rules> { res ->
        val request = call.receive<CreateRuleRequestDto>()
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            createRule(request.toDomainRequest(ws.id).bind()).bind()
        }
        call.respondEither<RuleDto, _>(result, HttpStatusCode.Created) { it.toDto() }
    }

    get<ApiV1.Workspaces.ById.Rules.ById> { res ->
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            val ruleId = parseId(res.id, "ruleId", ::RuleId).bind()
            val rule = ruleRepository.findById(ruleId) ?: raise(DomainError.RuleNotFound(ruleId))
            if (rule.workspaceId != ws.id) raise(DomainError.RuleNotFound(ruleId))
            rule
        }
        call.respondEither<RuleDto, _>(result) { it.toDto() }
    }

    put<ApiV1.Workspaces.ById.Rules.ById> { res ->
        val request = call.receive<CreateRuleRequestDto>()
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            val ruleId = parseId(res.id, "ruleId", ::RuleId).bind()
            val existing = ruleRepository.findById(ruleId) ?: raise(DomainError.RuleNotFound(ruleId))
            if (existing.workspaceId != ws.id) raise(DomainError.RuleNotFound(ruleId))
            updateRule(ruleId, request.toUpdateDomain().bind()).bind()
        }
        call.respondEither<RuleDto, _>(result) { it.toDto() }
    }

    delete<ApiV1.Workspaces.ById.Rules.ById> { res ->
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            val ruleId = parseId(res.id, "ruleId", ::RuleId).bind()
            val existing = ruleRepository.findById(ruleId) ?: raise(DomainError.RuleNotFound(ruleId))
            if (existing.workspaceId != ws.id) raise(DomainError.RuleNotFound(ruleId))
            deleteRule(ruleId).bind()
        }
        call.respondEither(result, HttpStatusCode.NoContent)
    }
}
