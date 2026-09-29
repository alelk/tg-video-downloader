package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.rule.CreateRuleRequestDto
import io.github.alelk.tgvd.api.contract.rule.RuleDto
import io.github.alelk.tgvd.api.contract.rule.RuleListResponseDto
import io.github.alelk.tgvd.api.mapping.rule.parseRuleId
import io.github.alelk.tgvd.api.mapping.rule.toDomainRequest
import io.github.alelk.tgvd.api.mapping.rule.toDto
import io.github.alelk.tgvd.api.mapping.rule.toUpdateDomain
import io.github.alelk.tgvd.domain.rule.CreateRuleUseCase
import io.github.alelk.tgvd.domain.rule.DeleteRuleUseCase
import io.github.alelk.tgvd.domain.rule.GetRuleUseCase
import io.github.alelk.tgvd.domain.rule.ListRulesUseCase
import io.github.alelk.tgvd.domain.rule.UpdateRuleUseCase
import io.github.alelk.tgvd.server.transport.auth.parseWorkspaceSlug
import io.github.alelk.tgvd.server.transport.auth.telegramUser
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.ruleRoutes() {
    ruleCollectionRoutes()
    ruleByIdRoutes()
}

/** `GET …/rules` and `POST …/rules`. */
private fun Route.ruleCollectionRoutes() {
    val listRules by inject<ListRulesUseCase>()
    val createRule by inject<CreateRuleUseCase>()

    get<ApiV1.Workspaces.ById.Rules> { res ->
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
                listRules(slug, call.telegramUser.id).bind()
            }
        call.respondEither<RuleListResponseDto, _>(result) { rules -> RuleListResponseDto(rules.map { it.toDto() }) }
    }

    post<ApiV1.Workspaces.ById.Rules> { res ->
        val body = call.receive<CreateRuleRequestDto>()
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
                val request = body.toDomainRequest().bind()
                createRule(slug, call.telegramUser.id, request).bind()
            }
        call.respondEither<RuleDto, _>(result, HttpStatusCode.Created) { it.toDto() }
    }
}

/** `GET`, `PUT` and `DELETE …/rules/{id}`. */
private fun Route.ruleByIdRoutes() {
    val getRule by inject<GetRuleUseCase>()
    val updateRule by inject<UpdateRuleUseCase>()
    val deleteRule by inject<DeleteRuleUseCase>()

    get<ApiV1.Workspaces.ById.Rules.ById> { res ->
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
                val ruleId = parseRuleId(res.id).bind()
                getRule(slug, call.telegramUser.id, ruleId).bind()
            }
        call.respondEither<RuleDto, _>(result) { it.toDto() }
    }

    put<ApiV1.Workspaces.ById.Rules.ById> { res ->
        val body = call.receive<CreateRuleRequestDto>()
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
                val ruleId = parseRuleId(res.id).bind()
                val request = body.toUpdateDomain().bind()
                updateRule(slug, call.telegramUser.id, ruleId, request).bind()
            }
        call.respondEither<RuleDto, _>(result) { it.toDto() }
    }

    delete<ApiV1.Workspaces.ById.Rules.ById> { res ->
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
                val ruleId = parseRuleId(res.id).bind()
                deleteRule(slug, call.telegramUser.id, ruleId).bind()
            }
        call.respondEither(result, HttpStatusCode.NoContent)
    }
}
