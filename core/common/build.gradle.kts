plugins {
    alias(libs.plugins.lumen.android.library)
    alias(libs.plugins.lumen.hilt)
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.android)
}