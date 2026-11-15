package tv.lumen.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

object LumenSdk {
    const val MIN = 24
    const val COMPILE = 37
    const val TARGET = 37
    val JAVA = JavaVersion.VERSION_17
}

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalStateException("Нет библиотеки '$alias' в libs.versions.toml") }

/** tv.lumen.core.model для :core:model и т. п. */
fun Project.lumenNamespace(): String = "tv.lumen" + path.replace(':', '.')
