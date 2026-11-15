plugins {
    alias(libs.plugins.lumen.android.library)
    alias(libs.plugins.lumen.hilt)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":data:youtube"))
    implementation(project(":data:sponsorblock"))
    implementation(project(":data:library"))
}