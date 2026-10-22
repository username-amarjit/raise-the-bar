import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module: every training rule lives here, with no Android, database or device imports.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
}

// The shared seed data in /data ships inside the engine jar under rtb-data/, so the app and the tests read the same files.
tasks.processResources {
    from(rootProject.layout.projectDirectory.dir("data")) {
        into("rtb-data")
        include("*.json")
    }
}

tasks.test {
    useJUnit()
    systemProperty("user.timezone", "Asia/Kolkata")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.named("check") { dependsOn(":secretScan") }
