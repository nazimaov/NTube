plugins {
    alias(libs.plugins.lumen.android.library)
    alias(libs.plugins.lumen.hilt)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.datastore.preferences)
}