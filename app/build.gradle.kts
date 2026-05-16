plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.project.vortex.callsagent"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.project.vortex.callsagent"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Tab A9+ (SM-X216) ships arm64 only. Stripping other ABIs
        // shaves the Linphone native payload to a single .so set.
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a")
        }

        // Default API base URL points to Railway production.
        // Override per buildType below to hit a local backend instead.
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"https://crediiz-core-production.up.railway.app/api/\"",
        )

        // SIP credentials are no longer baked at build-time. They come
        // from the backend per-agent (`GET /voip-accounts/me`); see
        // `data/voip/VoipAccountRepository.kt` and
        // `docs/SESSION_AND_VOIP_INTEGRATION.md`.
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // Uncomment to use a local backend via Android emulator loopback.
            // buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3000/api/\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    // Let local JVM unit tests stub `android.util.Log` etc. with
    // default return values instead of crashing with
    // `java.lang.RuntimeException: Method ... not mocked`. Saves us
    // from adding Robolectric or mockk for trivial logging mocks.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt (DI) — using KSP (kapt is incompatible with AGP 9.1.1 built-in Kotlin)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    implementation(libs.core.ktx)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    // Room (local DB)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Retrofit + OkHttp + Moshi
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // DataStore (JWT)
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Linphone SDK (SIP/VoIP outbound calling against Voselia)
    implementation(libs.linphone.sdk.android)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
