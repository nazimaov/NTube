plugins {
    alias(libs.plugins.lumen.android.feature)
}

dependencies {
    implementation(project(":player"))
    implementation(project(":data:updater"))
    implementation(project(":data:ryd"))
    implementation(project(":data:youtube"))
    implementation(project(":core:datastore"))
    implementation(libs.media3.ui)
    implementation(libs.androidx.activity.compose)
}