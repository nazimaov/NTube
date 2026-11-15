plugins {
    alias(libs.plugins.lumen.android.library)
    alias(libs.plugins.lumen.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    api(project(":core:datastore"))
    implementation(project(":core:network"))

    testImplementation(libs.kotlinx.coroutines.test)
}