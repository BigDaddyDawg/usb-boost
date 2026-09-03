plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.usbboost.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.usbboost.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 18
        versionName = "2.6.1"
    }

    signingConfigs {
        create("stable") {
            storeFile = file("usb-boost.p12")
            storePassword = "usbboost"
            keyAlias = "usbboost"
            keyPassword = "usbboost"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Same key every CI run so Check for update can overwrite the installed app.
            signingConfig = if (file("usb-boost.p12").exists()) {
                signingConfigs.getByName("stable")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    testImplementation("junit:junit:4.13.2")
}
