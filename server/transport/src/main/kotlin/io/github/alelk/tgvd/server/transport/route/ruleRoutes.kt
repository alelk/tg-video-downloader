package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.rule.CreateRuleRequestDto
import io.github.alelk.tgvd.api.contract.rule.RuleDto
import io.github.alelk.tgvd.api.contract.rule.RuleListResponseDto
import io.github.alelk.tgvd.api.mapping.metadata.toDomain
import io.github.alelk.tgvd.api.mapping.rule.toDomain
import io.github.alelk.tgvd.api.mapping.rule.toDto
import io.github.alelk.tgvd.api.mapping.storage.toDomain
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.rule.Rule
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.server.transport.auth.telegramUser
import io.github.alelk.tgvd.server.transport.util.parseId
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun Route.ruleRoutes() {
    val ruleRepository by inject<RuleRepository>()

    get<ApiV1.Rules> {
        val user = call.telegramUser
        val rules = ruleRepository.findByOwner(user.id)
        call.respond(RuleListResponseDto(items = rules.map { it.toDto() }))
    }

    post<ApiV1.Rules> {
        val request = call.receive<CreateRuleRequestDto>()
        val user = call.telegramUser

        val result = either {
            val match = request.match.toDomain().bind()
            val now = Clock.System.now()
            val rule = Rule(
                id = RuleId(Uuid.random()),
                name = request.name,
                ownerId = user.id,
                match = match,
                metadataTemplate = request.metadataTemplate.toDomain(),
                storagePolicy = request.storagePolicy.toDomain(),
                downloadPolicy = request.downloadPolicy.toDomain(),
                postProcessPolicy = request.postProcessPolicy.toDomain(),
                enabled = request.enabled,
                priority = request.priority,
                createdAt = now,
                updatedAt = now,
            )
            ruleRepository.save(rule).bind()
        }

        call.respondEither<RuleDto, _>(result, HttpStatusCode.Created) { it.toDto() }
    }

    get<ApiV1.Rules.ById> { res ->
        val result = either {
            val ruleId = parseId(res.id, "ruleId", ::RuleId).bind()
            ruleRepository.findById(ruleId) ?: raise(DomainError.RuleNotFound(ruleId))
        }
        call.respondEither<RuleDto, _>(result) { it.toDto() }
    }

    put<ApiV1.Rules.ById> { res ->
        val request = call.receive<CreateRuleRequestDto>()

        val result = either {
            val ruleId = parseId(res.id, "ruleId", ::RuleId).bind()
            val existing = ruleRepository.findById(ruleId) ?: raise(DomainError.RuleNotFound(ruleId))
            val match = request.match.toDomain().bind()
            val updated = existing.copy(
                name = request.name,
                match = match,
                metadataTemplate = request.metadataTemplate.toDomain(),
                storagePolicy = request.storagePolicy.toDomain(),
                downloadPolicy = request.downloadPolicy.toDomain(),
                postProcessPolicy = request.postProcessPolicy.toDomain(),
                enabled = request.enabled,
                priority = request.priority,
                updatedAt = Clock.System.now(),
            )
            ruleRepository.save(updated).bind()
        }

        call.respondEither<RuleDto, _>(result) { it.toDto() }
    }

    delete<ApiV1.Rules.ById> { res ->
        val result = either {
            val ruleId = parseId(res.id, "ruleId", ::RuleId).bind()
            if (!ruleRepository.delete(ruleId)) raise(DomainError.RuleNotFound(ruleId))
        }
        call.respondEither(result, HttpStatusCode.NoContent)
    }
}

