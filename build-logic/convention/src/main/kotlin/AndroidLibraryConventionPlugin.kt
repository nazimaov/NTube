import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import tv.lumen.buildlogic.LumenSdk
import tv.lumen.buildlogic.lib
import tv.lumen.buildlogic.libs
import tv.lumen.buildlogic.lumenNamespace

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            namespace = lumenNamespace()
            compileSdk = LumenSdk.COMPILE
            defaultConfig {
                minSdk = LumenSdk.MIN
            }
            compileOptions {
                sourceCompatibility = LumenSdk.JAVA
                targetCompatibility = LumenSdk.JAVA
                isCoreLibraryDesugaringEnabled = true
            }
        }
        dependencies {
            add("coreLibraryDesugaring", libs.lib("desugar-jdk-libs-nio"))
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        }
    }
}
