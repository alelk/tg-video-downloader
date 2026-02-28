package io.github.alelk.tgvd.api.contract.resource

import io.ktor.resources.*
import kotlinx.serialization.Serializable

@Serializable
@Resource("/api/v1")
class ApiV1 {

    @Serializable
    @Resource("preview")
    class Preview(val parent: ApiV1 = ApiV1())

    @Serializable
    @Resource("jobs")
    class Jobs(
        val parent: ApiV1 = ApiV1(),
        val status: String? = null,
        val limit: Int = 20,
        val offset: Int = 0
    ) {
        @Serializable
        @Resource("{id}")
        class ById(val parent: Jobs, val id: String) {

            @Serializable
            @Resource("cancel")
            class Cancel(val parent: ById)
        }
    }

    @Serializable
    @Resource("rules")
    class Rules(val parent: ApiV1 = ApiV1()) {
        @Serializable
        @Resource("{id}")
        class ById(val parent: Rules, val id: String)
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
