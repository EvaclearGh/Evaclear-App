import java.util.Properties

plugins {
    id("com.android.application")
}

// ---- App settings you may want to change -------------------------------------------------
val websiteUrl = "https://www.evacleartradingenterprise.com/"
// Every new upload to Google Play needs a higher versionCode.
val appVersionCode = 1
val appVersionName = "1.0.0"
// ------------------------------------------------------------------------------------------

// Release signing: keystore.properties (local) or environment variables (GitHub Actions).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    (keystoreProps.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "EVACLEAR_KEYSTORE_FILE")

android {
    namespace = "com.evacleartradingenterprise.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.evacleartradingenterprise.app"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "START_URL", "\"$websiteUrl\"")
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("storePassword", "EVACLEAR_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "EVACLEAR_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "EVACLEAR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
