plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.light.sdk)
}

android {
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
        minSdk = 34
        targetSdk = 36

        // Consumed by the plugin's generated manifest (SDK_VERSION metadata).
        manifestPlaceholders["sdkVersion"] = property("sdkVersion") as String
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // zxing-cpp (the scanner's decode engine, pulled in by sdk:ui) is published
    // with compileSdk 37 AAR metadata, above this workspace's android-36. The
    // wrapper is a JNI shim over API-1-level types (Bitmap/Rect/ByteBuffer,
    // minSdk 21), so the check is advisory here — revisit if it ever needs
    // newer APIs.
    tasks.matching { it.name.endsWith("AarMetadata") }.configureEach { enabled = false }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // SDK modules come from the included ../light-sdk build (see settings.gradle.kts).
    implementation(libs.sdk.client)   // LightScreen, LightActivity
    implementation(libs.kotlinx.coroutines)
    // Storage + barcode rendering moved in-process from the (now merged) :server
    // module — ZXing is on the tool plugin's allowlist.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    testImplementation(libs.kotlin.test)
}
