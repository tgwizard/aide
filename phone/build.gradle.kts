plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.tgwizard.aide"
    compileSdk = 37
    defaultConfig {
        // Must match the wear module so the Wearable Data Layer pairs the two apps.
        applicationId = "com.tgwizard.aide"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }
    // Release key comes from the environment (see deploy.sh); without it, fall back to the debug
    // key so the public repo still builds. Phone and watch must be signed with the same key.
    val releaseKeystore = System.getenv("AIDE_KEYSTORE")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storeType = "pkcs12"
                storePassword = System.getenv("AIDE_KEYSTORE_PASSWORD")
                keyAlias = "aide"
                keyPassword = System.getenv("AIDE_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.okhttp)
}
