import org.jetbrains.compose.ComposeExtension

// KMP library module that renders UI with Compose Multiplatform (`features`). Same single-classpath
// reason as `tgvd.kmp.serialization`: the Compose and Compose-compiler plugins are applied here and
// never via `alias(...)` in a module.

plugins {
    id("tgvd.kmp")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The type-safe `compose` accessor only exists in scripts that apply the Compose plugin in their own
// `plugins {}` block; modules get the plugin through this convention, so the dependency notations
// are read off the extension. The artifacts every Compose UI module needs are declared once, here.
val composeDeps = extensions.getByType<ComposeExtension>().dependencies

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(composeDeps.runtime)
            implementation(composeDeps.foundation)
            implementation(composeDeps.material3)
            implementation(composeDeps.ui)
            implementation(composeDeps.components.resources)
        }
    }
}
