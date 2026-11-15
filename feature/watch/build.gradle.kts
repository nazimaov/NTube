plugins {
    alias(libs.plugins.lumen.android.feature)
}

dependencies {
    implementation(project(":player"))
    implementation(libs.media3.ui)
    implementation(libs.androidx.activity.compose)
}