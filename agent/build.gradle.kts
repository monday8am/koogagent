plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation("org.json:json:20250517")
    implementation(libs.koog.agents) {
        // Exclude to avoid duplicate classes with kotlin-sdk-client
        exclude(group = "io.modelcontextprotocol", module = "kotlin-sdk-core-jvm")
    }
    testImplementation(kotlin("test"))
}
