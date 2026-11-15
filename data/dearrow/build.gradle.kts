plugins {
    alias(libs.plugins.lumen.android.library)
    alias(libs.plugins.lumen.hilt)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
}