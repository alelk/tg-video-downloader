package io.github.alelk.tgvd.api.contract.resource

import io.ktor.resources.*
import kotlinx.serialization.Serializable

/**
 * Type-safe API resource hierarchy.
 *
 * All domain resources (jobs, rules, preview) are scoped under a workspace:
 * ```
 * /api/v1/workspaces                              — list user's workspaces
 * /api/v1/workspaces/{id}                          — workspace details
 * /api/v1/workspaces/{id}/members                  — workspace members
 * /api/v1/workspaces/{id}/preview                  — preview a URL
 * /api/v1/workspaces/{id}/jobs                     — jobs within workspace
 * /api/v1/workspaces/{id}/rules                    — rules within workspace
 * /api/v1/system/...                               — system-wide (yt-dlp status, update)
 * ```
 */
@Serializable
@Resource("/api/v1")
class ApiV1 {

    @Serializable
    @Resource("workspaces")
    class Workspaces(val parent: ApiV1 = ApiV1()) {

        @Serializable
        @Resource("{workspaceId}")
        class ById(val parent: Workspaces = Workspaces(), val workspaceId: String) {

            // --- Members ---

            @Serializable
            @Resource("members")
            class Members(val parent: ById) {
                @Serializable
                @Resource("{userId}")
                class ByUserId(val parent: Members, val userId: Long)
            }

            // --- Preview ---

            @Serializable
            @Resource("preview")
            class Preview(val parent: ById)

            // --- Jobs ---

            @Serializable
            @Resource("jobs")
            class Jobs(
                val parent: ById,
                val status: String? = null,
                val limit: Int = 20,
                val offset: Int = 0,
            ) {
                @Serializable
                @Resource("{id}")
                class ById(val parent: Jobs, val id: String) {
                    @Serializable
                    @Resource("cancel")
                    class Cancel(val parent: ById)
                }
            }

            // --- Rules ---

            @Serializable
            @Resource("rules")
            class Rules(val parent: ById) {
                @Serializable
                @Resource("{id}")
                class ById(val parent: Rules, val id: String)
            }
        }
    }

    @Serializable
    @Resource("system")
    class System(val parent: ApiV1 = ApiV1()) {
        @Serializable
        @Resource("yt-dlp")
        class YtDlp(val parent: System) {
            @Serializable
            @Resource("status")
            class Status(val parent: YtDlp)

            @Serializable
            @Resource("update")
            class Update(val parent: YtDlp)
        }
    }
}
