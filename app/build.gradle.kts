plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// One version, kept in gradle.properties. versionCode has to rise with every
// release, so it's derived from it: 0.1.0 -> 100, 1.2.3 -> 10203.
val appVersion = providers.gradleProperty("appVersion").get()
val (major, minor, patch) = appVersion.split(".").map { it.toInt() }

// Release signing comes from the environment (GitHub Actions secrets), never
// from the repo. Without it the release build is left unsigned.
val keystorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")

android {
    namespace = "io.github.byjoelsamuel.drivesanity"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.byjoelsamuel.drivesanity"
        minSdk = 26
        targetSdk = 35
        versionCode = major * 10000 + minor * 100 + patch
        versionName = appVersion
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.core:core-ktx:1.17.0")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in plain unit tests; this is the real one.
    testImplementation("org.json:json:20240303")
}
