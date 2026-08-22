import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    // Placeholder — rename before Play Store submission (applicationId must be
    // unique and permanent once published). Android Studio's Refactor > Rename
    // Package handles this safely across the whole project.
    namespace = "com.venunair.warden"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.venunair.warden"
        minSdk = 26 // Android 8.0 — needed for NotificationChannel (Sprint 5) without a compat shim
        targetSdk = 36
        // Bumped with each meaningful fix/feature from here on -- previously
        // stuck at 1/"0.1.0-sprint1" since Sprint 0, which made "did you
        // rebuild with the latest code" impossible to self-check. Check
        // Settings > Apps > Warden > App details (or long-press the icon >
        // App info) to see versionName on-device before re-testing a fix.
        versionCode = 2
        versionName = "0.4.2-sprint4-ocr-datefix"

        vectorDrawables { useSupportLibrary = true }
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

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Kotlin's `android.kotlinOptions {}` DSL is deprecated (kotl.in/u1r8ln) in favor
// of this top-level `compilerOptions` block, which is a separate, non-Android
// extension the Kotlin Gradle plugin adds — hence it lives outside `android {}`,
// not nested inside it.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Sprint 5: local reminder scheduling
    implementation(libs.work.runtime.ktx)

    // Sprint 2: camera capture
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Sprint 4: on-device OCR (image + rendered PDF pages)
    implementation(libs.mlkit.text.recognition)

    implementation(libs.kotlinx.coroutines.android)
}
