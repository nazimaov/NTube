import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import tv.lumen.buildlogic.LumenSdk
import tv.lumen.buildlogic.lib
import tv.lumen.buildlogic.libs

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = LumenSdk.COMPILE
            defaultConfig {
                minSdk = LumenSdk.MIN
                targetSdk = LumenSdk.TARGET
            }
            compileOptions {
                sourceCompatibility = LumenSdk.JAVA
                targetCompatibility = LumenSdk.JAVA
                isCoreLibraryDesugaringEnabled = true
            }
        }
        dependencies {
            add("coreLibraryDesugaring", libs.lib("desugar-jdk-libs-nio"))
        }
    }
}
