plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.goldan.mdosdex"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.goldan.mdosdex"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
    }

    flavorDimensions += "device"
    productFlavors {
        create("phone") {
            dimension = "device"
            applicationIdSuffix = ".phone"
            versionNameSuffix = "-phone"
        }
        create("tv") {
            dimension = "device"
            applicationIdSuffix = ".tv"
            versionNameSuffix = "-tv"
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
        debug { }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
    add("phoneImplementation", "com.journeyapps:zxing-android-embedded:4.3.0")
}
