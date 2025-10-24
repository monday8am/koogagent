plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)

    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    // Project dependencies
    implementation(project(":data"))
    implementation(project(":agent"))

    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(kotlin("test"))
}
