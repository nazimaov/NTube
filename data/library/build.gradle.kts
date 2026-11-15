plugins {
    alias(libs.plugins.lumen.android.library)
    alias(libs.plugins.lumen.hilt)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    api(project(":core:datastore"))

    testImplementation(libs.kotlinx.coroutines.test)
}