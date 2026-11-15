plugins {
    alias(libs.plugins.lumen.android.feature)
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":data:library"))
    implementation(project(":data:updater"))
}