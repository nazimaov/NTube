import java.util.Properties

plugins {
    alias(libs.plugins.lumen.android.application)
    alias(libs.plugins.lumen.android.compose)
    alias(libs.plugins.lumen.hilt)
    alias(libs.plugins.kotlin.serialization)
}

val appName = providers.gradleProperty("lumen.appName").get()

// Подпись release (SPEC 10.3): keystore.properties (не в git) или переменные окружения LUMEN_*.
// Без ключа release собирается неподписанным — для проверки R8 этого достаточно.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
fun signingValue(property: String, env: String): String? =
    keystoreProps.getProperty(property) ?: providers.environmentVariable(env).orNull
val releaseStoreFile = signingValue("storeFile", "LUMEN_KEYSTORE")

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

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "LUMEN_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "LUMEN_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "LUMEN_KEY_PASSWORD")
            }
        }
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
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":data:youtube"))
    implementation(project(":data:library"))
    implementation(project(":data:updater"))
    implementation(project(":data:dearrow"))

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
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
}