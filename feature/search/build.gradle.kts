plugins {
    alias(libs.plugins.lumen.android.feature)
}

dependencies {
    implementation(project(":data:youtube"))
    implementation(project(":data:library"))
    implementation(libs.androidx.activity.compose)
}