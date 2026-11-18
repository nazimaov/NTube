plugins {
    alias(libs.plugins.lumen.android.library)
    alias(libs.plugins.lumen.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
}