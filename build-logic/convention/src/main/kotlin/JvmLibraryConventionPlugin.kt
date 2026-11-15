import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import tv.lumen.buildlogic.lib
import tv.lumen.buildlogic.libs

/** Чистый Kotlin без Android SDK (core:model). Kotlin берёт toolchain из java-расширения. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
        }
        dependencies {
            add("testImplementation", libs.lib("junit"))
        }
    }
}
