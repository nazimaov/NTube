import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import tv.lumen.buildlogic.lib
import tv.lumen.buildlogic.libs

/** Модуль экрана: библиотека + Compose + Hilt + общие core-модули. Модули feature друг от друга не зависят. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("lumen.android.library")
        pluginManager.apply("lumen.android.compose")
        pluginManager.apply("lumen.hilt")

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            add("testImplementation", libs.lib("turbine"))
        }
    }
}
