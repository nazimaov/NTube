plugins {
    alias(libs.plugins.lumen.android.feature)
}

dependencies {
    implementation(project(":data:library"))
    implementation(project(":data:account"))
    implementation(project(":data:youtube"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.activity.compose)
}
