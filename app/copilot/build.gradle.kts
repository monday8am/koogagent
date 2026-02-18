import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
    namespace = "com.monday8am.koogagent.copilot"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.monday8am.koogagent.copilot"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AppAuth redirect scheme placeholder (required by AppAuth library manifest)
        // Copilot doesn't use OAuth, but placeholder is needed for manifest merger
        manifestPlaceholders["appAuthRedirectScheme"] = "copilot"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = getKeystoreProperty("storeFile", "KEYSTORE_FILE")
            if (storeFilePath != null) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = getKeystoreProperty("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = getKeystoreProperty("keyAlias", "KEY_ALIAS")
                keyPassword = getKeystoreProperty("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }

    packaging { resources { excludes += "META-INF/*" } }

    buildFeatures {
        compose = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = false
    }
}

dependencies {
    // Core module brings all infrastructure
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
