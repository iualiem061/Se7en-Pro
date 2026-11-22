import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Optional release signing: place keystore/se7en-release.keystore next to app module
// plus keystore/keystore.properties (storePassword=..., keyAlias=..., keyPassword=...).
// If missing, release builds fall back to debug signing (safe for CI).
val keystoreDir = file("keystore")
val keystoreFile = File(keystoreDir, "se7en-release.keystore")
val keystorePropsFile = File(keystoreDir, "keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.kng7.sevenpro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kng7.sevenpro"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
        resourceConfigurations += listOf("en", "fa", "ru")
    }

    signingConfigs {
        if (keystoreFile.exists() && keystorePropsFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreFile.exists() && keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // Per-ABI APKs + universal, all packaged & signed by AGP (keeps resources.arsc
    // uncompressed and aligned as required on Android 11+).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    // Compress native libs into the APK (extracted on install) -> much smaller downloads,
    // while AGP still keeps resources.arsc uncompressed + aligned (Android 11+ requirement).
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // hev-socks5-tunnel: official prebuilt AAR (JNI tunnel bridge, hev.htproxy.TProxyService)
    implementation(files("libs/hev-socks5-tunnel.aar"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.code.gson:gson:2.11.0")
}
