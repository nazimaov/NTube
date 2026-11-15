plugins {
    alias(libs.plugins.lumen.android.feature)
}

dependencies {
    implementation(project(":data:youtube"))
    implementation(libs.androidx.activity.compose)
}