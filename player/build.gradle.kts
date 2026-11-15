plugins {
    alias(libs.plugins.lumen.android.library)
    alias(libs.plugins.lumen.hilt)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":data:youtube"))
    implementation(project(":data:sponsorblock"))
    implementation(project(":data:library"))

    // Плеер — Media3 одной версии (SPEC 5.1). exoplayer отдаёт media3-common наружу (Player).
    api(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.session)
}
