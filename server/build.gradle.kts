plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lightphone.passes.server"
    compileSdk = 36

    signingConfigs {
        // Workspace dev signing (same key as the SDK tools/emulator).
        create("lightsdkDev") {
            storeFile = file("../../light-sdk/sdk/keys/lightsdk-dev.jks")
            storePassword = "android"
            keyAlias = "lightsdk-dev"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.lightphone.passes.server"
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // SDK modules come from the included ../light-sdk build (see settings.gradle.kts).
    // The status screen's SDK deps pull the SDK's CameraX + ML Kit barcode
    // scanner, which this companion never uses — prune it (saves ~20 MB of
    // native libbarhopper_v3.so). Exclusions are per-subtree: both direct
    // deps that reach it must exclude, or it leaks back in.
    implementation(libs.sdk.server)   // LightSdkServer + LightSdkService (the binder)
    implementation(libs.sdk.client) { // client runtime pieces the server reuses
        exclude(group = "com.google.mlkit")
        exclude(group = "androidx.camera")
        exclude(group = "com.google.android.gms")
    }
    implementation(libs.sdk.ui) {     // Light design system for the status screen
        exclude(group = "com.google.mlkit")
        exclude(group = "androidx.camera")
        exclude(group = "com.google.android.gms")
    }
    implementation(libs.compose.activity)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    // Barcode rendering lives in the companion (the tool may not use ZXing).
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
}
