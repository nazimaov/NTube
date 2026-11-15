plugins {
    alias(libs.plugins.lumen.android.feature)
}

dependencies {
    implementation(project(":player"))
    implementation(project(":data:updater"))
    implementation(libs.media3.ui)
    implementation(libs.androidx.activity.compose)
}