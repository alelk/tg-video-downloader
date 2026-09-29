import org.gradle.api.Project
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec

/**
 * Repositories are declared only in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`). By default the
 * Kotlin/JS tooling adds its own ivy repositories for the Node.js and Yarn distributions to the
 * project that downloads them, which that mode rejects. Unsetting `downloadBaseUrl` stops that;
 * the same two repositories are declared in settings instead, so the downloads still work.
 *
 * Applied to this project and to the root project: the Kotlin plugin installs the Node.js/Yarn
 * setup tasks (`:kotlinNodeJsSetup`, `:kotlinYarnSetup`) on the root project.
 */
fun Project.resolveJsToolingFromSettingsRepositories() {
    setOf(this, rootProject).forEach { target ->
        target.plugins.withType<NodeJsPlugin> {
            target.the<NodeJsEnvSpec>().downloadBaseUrl.set(null as String?)
        }
        target.plugins.withType<YarnPlugin> {
            target.the<YarnRootEnvSpec>().downloadBaseUrl.set(null as String?)
        }
    }
}
