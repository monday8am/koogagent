plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)

    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kermit)
    api(libs.okhttp)
    api(libs.json)
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(kotlin("test"))
}
