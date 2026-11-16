plugins {
    alias(libs.plugins.lumen.android.feature)
}

dependencies {
    implementation(project(":data:library"))
    implementation(project(":data:account"))
}
