plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.litertlm.android)
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.localagents.fc)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
