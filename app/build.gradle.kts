import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // kotlin.android plugin removed - built into AGP 9.0
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// Load keystore properties from file or environment variables
val keystorePropertiesFile = rootProject.file("signing/keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            load(keystorePropertiesFile.inputStream())
        }
    }

fun getKeystoreProperty(key: String, envVar: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(envVar)

android {
    namespace = "com.monday8am.koogagent"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.monday8am.koogagent"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = getKeystoreProperty("storeFile", "KEYSTORE_FILE")
            if (storeFilePath != null) {
                // Resolve relative to project root (works for both local and CI)
                storeFile = rootProject.file(storeFilePath)
                storePassword = getKeystoreProperty("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = getKeystoreProperty("keyAlias", "KEY_ALIAS")
                keyPassword = getKeystoreProperty("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    packaging {
        resources {
            excludes += "META-INF/*"
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        // Suppress warnings for prototype
        disable += setOf(
            "ComposeModifierMissing", // Too strict for prototype
            "IconDipSize", // Icon density mismatch in xhdpi (not critical for prototype)
            "AndroidGradlePluginVersion", // Using AGP 9.0 rc2 intentionally
        )
    }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":agent")) {
        exclude(group = "com.google.ai.edge.litertlm", module = "litertlm-jvm")
    }
    implementation(project(":presentation")) {
        exclude(group = "com.google.ai.edge.litertlm", module = "litertlm-jvm")
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.security.crypto)

    implementation(libs.litertlm.android)
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.localagents.fc)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    lintChecks(libs.compose.lint.checks)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
