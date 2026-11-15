plugins {
    alias(libs.plugins.lumen.android.library)
    alias(libs.plugins.lumen.android.compose)
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.tv.material)
    api(libs.androidx.compose.material.icons.core)
    api(libs.coil.compose)
}