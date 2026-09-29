import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** Detekt sources of a Kotlin Multiplatform module (tgvd.kmp and everything built on it). */
internal val KMP_DETEKT_SOURCES = listOf(
    "src/commonMain/kotlin",
    "src/commonTest/kotlin",
    "src/jvmMain/kotlin",
    "src/jvmTest/kotlin",
    "src/jsMain/kotlin",
)

/** Detekt sources of the js-only Mini App shell (tgvd.compose.js). */
internal val JS_SHELL_DETEKT_SOURCES = listOf("src/jsMain/kotlin")

/** Detekt sources of a Kotlin/JVM module (tgvd.jvm and everything built on it). */
internal val JVM_DETEKT_SOURCES = listOf("src/main/kotlin", "src/test/kotlin", "src/testFixtures/kotlin")

/**
 * Detekt + ktlint wiring shared by the tgvd.kmp, tgvd.compose.js and tgvd.jvm conventions (the
 * plugins themselves are applied in each convention's `plugins {}` block). Both run in
 * `./gradlew build` through `check`.
 *
 * Tests are analysed on purpose: they are where copy-paste accumulates fastest. Each module keeps
 * its own `detekt-baseline.xml` / `ktlint-baseline.xml`: findings that pre-date static analysis stay
 * recorded, so the gate blocks NEW debt. Baselines only shrink — never regenerate one to absorb new
 * findings. A missing file means an empty baseline.
 */
internal fun Project.configureStaticAnalysis(detektSources: List<String>) {
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("detekt.yml"))
        source.setFrom(detektSources)
        baseline = file("detekt-baseline.xml")
    }

    extensions.configure<KtlintExtension> {
        baseline.set(file("ktlint-baseline.xml"))
        val buildDir = layout.buildDirectory.asFile
        filter {
            // Compose resource accessors and BuildConfig are generated. The build dir may be
            // relocated outside the project, so match the path patterns AND the build dir itself.
            exclude { element ->
                val file = element.file
                file.path.contains("/generated/compose/") ||
                    file.path.contains("/build/generated/") ||
                    file.startsWith(buildDir.get())
            }
        }
    }
}
