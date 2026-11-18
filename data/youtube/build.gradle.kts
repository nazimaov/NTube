plugins {
    alias(libs.plugins.lumen.android.library)
    alias(libs.plugins.lumen.hilt)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))

    // Единственный модуль, который видит org.schabi.newpipe.* (SPEC, раздел 3)
    implementation(libs.newpipe.extractor)

    testImplementation(libs.kotlinx.coroutines.test)
    // Настоящий org.json для тестов разбора: в Android SDK для модульных тестов он заглушка
    testImplementation(libs.org.json)
}

// Живые запросы к YouTube (SPEC, раздел 12). Не входят в `test`: тесты *LiveTest
// пропускаются без свойства lumen.liveTest. Запуск: ./gradlew :data:youtube:liveTest
val unitTestTask = "testDebugUnitTest"
tasks.register<Test>("liveTest") {
    group = "verification"
    description = "Живые интеграционные тесты YouTube (сеть)."
    val unit = tasks.named<Test>(unitTestTask).get()
    dependsOn(unit.dependsOn)
    testClassesDirs = unit.testClassesDirs
    classpath = unit.classpath
    systemProperty("lumen.liveTest", "true")
    filter { includeTestsMatching("*LiveTest") }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    outputs.upToDateWhen { false }
}
