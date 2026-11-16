import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is optional and kept out of the repository: local.properties may point,
// with `release.signing=<path>`, to a properties file holding storeFile, storePassword,
// keyAlias and keyPassword. Without it, the release build is left unsigned.
val releaseSigning: Properties? = run {
    val local = Properties()
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(local::load)
    local.getProperty("release.signing")?.let(::File)?.takeIf { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }
}

android {
    namespace = "adidoo.mateview"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "adidoo.mateview"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"
    }

    signingConfigs {
        releaseSigning?.let { signing ->
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            // Phones only: the map library also ships x86 libraries for emulators.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
        // The release code, shrunk the same way but signed with the debug key, so that it can be
        // installed over a debug build to test the shrinking without losing the app's settings.
        create("releaseTest") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.maplibre)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
