plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
        compose = true
    }
    testOptions {
        unitTests {
            // Robolectric needs the app's resources and assets (the demo drive is one).
            isIncludeAndroidResources = true
            all {
                // Where there's no network, point ROBOLECTRIC_DIR at a folder that already has the Android jars.
                System.getenv("ROBOLECTRIC_DIR")?.let { dir ->
                    it.systemProperty("robolectric.offline", "true")
                    it.systemProperty("robolectric.dependency.dir", dir)
                }
            }
        }
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

    // The phone screens (Android Auto draws its own, in car/).
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.12.4")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in plain unit tests; this is the real one.
    testImplementation("org.json:json:20240303")
    // Runs the app's own code, the Android parts included, in plain unit tests (see DemoFlowTest).
    testImplementation("org.robolectric:robolectric:4.16")
}
