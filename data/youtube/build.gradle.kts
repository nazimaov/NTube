plugins {
    alias(libs.plugins.lumen.android.library)
    alias(libs.plugins.lumen.hilt)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    // NewPipeExtractor подключается в фазе 1. Только этот модуль видит org.schabi.newpipe.*
}