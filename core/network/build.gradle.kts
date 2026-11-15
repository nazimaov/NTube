plugins {
    alias(libs.plugins.lumen.android.library)
    alias(libs.plugins.lumen.hilt)
}

dependencies {
    implementation(project(":core:common"))
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
}
