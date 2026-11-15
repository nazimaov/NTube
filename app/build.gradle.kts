plugins {
    alias(libs.plugins.lumen.android.application)
    alias(libs.plugins.lumen.android.compose)
    alias(libs.plugins.lumen.hilt)
    alias(libs.plugins.kotlin.serialization)
}

val appName = providers.gradleProperty("lumen.appName").get()

android {
    namespace = "tv.lumen.app"

    defaultConfig {
        applicationId = "tv.lumen.app"
        versionCode = providers.gradleProperty("lumen.versionCode").get().toInt()
        versionName = providers.gradleProperty("lumen.versionName").get()
        resValue("string", "app_name", appName)
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"${providers.gradleProperty("lumen.updateRepoOwner").get()}\"")
        buildConfigField("String", "UPDATE_REPO_NAME", "\"${providers.gradleProperty("lumen.updateRepoName").get()}\"")
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "$appName Debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":data:youtube"))

    implementation(project(":feature:home"))
    implementation(project(":feature:search"))
    implementation(project(":feature:channel"))
    implementation(project(":feature:playlist"))
    implementation(project(":feature:watch"))
    implementation(project(":feature:library"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}