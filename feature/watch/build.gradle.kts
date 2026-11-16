plugins {
    alias(libs.plugins.lumen.android.feature)
}

dependencies {
    implementation(project(":player"))
    implementation(project(":data:updater"))
    implementation(project(":data:ryd"))
    implementation(project(":data:library"))
    implementation(project(":data:account"))
    implementation(project(":core:common"))
    implementation(project(":data:youtube"))
    implementation(project(":core:datastore"))
    implementation(libs.media3.ui)
    implementation(libs.androidx.activity.compose)
}