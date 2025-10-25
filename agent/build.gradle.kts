plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":data"))
    api(libs.koog.agents) {
        // Exclude to avoid duplicate classes with kotlin-sdk-client
        exclude(group = "io.modelcontextprotocol", module = "kotlin-sdk-core-jvm")
    }
    api(libs.kermit)
    testImplementation(kotlin("test"))
}
