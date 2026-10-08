plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.light.sdk)
}

android {
    compileSdk = rootProject.ext["compileSdk"] as Int

    signingConfigs {
        // Workspace dev signing (same key as the SDK tools/emulator). Inside
        // an SDK checkout (Light's Tool Library builder stages this tool/
        // module into the baked-in SDK repo) the keys live at ../sdk/keys.
        create("lightsdkDev") {
            storeFile = file(
                listOf("../../light-sdk/sdk/keys/lightsdk-dev.jks", "../sdk/keys/lightsdk-dev.jks")
                    .map(::file).first { it.exists() }
            )
            storePassword = "android"
            keyAlias = "lightsdk-dev"
            keyPassword = "android"
        }
    }

    defaultConfig {
        minSdk = rootProject.ext["minSdk"] as Int
        targetSdk = rootProject.ext["targetSdk"] as Int

        // Consumed by the plugin's generated manifest (SDK_VERSION metadata).
        manifestPlaceholders["sdkVersion"] = property("sdkVersion") as String

        // The two runtimes this tool ships for: the LP3 (arm64) and the dev
        // emulator (x86_64) — drops zxing-cpp's x86 + armeabi-v7a libs
        // (~3 MB, release feedback 2026-09-21).
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
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
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(rootProject.ext["jvmTarget"] as String))
    }
}

// Inside an SDK checkout the SDK modules are sibling projects; in this
// workspace the included ../light-sdk build substitutes the module artifacts.
val inSdkRepo = file("../sdk").exists()

dependencies {
    implementation(
        if (inSdkRepo) project(":sdk:client")
        else "com.thelightphone:sdk-client"   // LightScreen, LightActivity
    )
    implementation(libs.kotlinx.coroutines)
    // Storage + barcode rendering moved in-process from the (now merged) :server
    // module — ZXing is on the tool plugin's allowlist.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    testImplementation(libs.kotlin.test)
}
