plugins {
    alias(libs.plugins.lumen.android.feature)
}

dependencies {
    implementation(project(":data:youtube"))
    implementation(project(":data:library"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.activity.compose)
}
