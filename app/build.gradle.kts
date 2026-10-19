plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.oniolla.interviewer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.oniolla.interviewer"
        minSdk = 24
        targetSdk = 37
        versionCode = 5
        versionName = "1.0.5"

        val googleWebClientId = providers.gradleProperty("GOOGLE_WEB_CLIENT_ID")
            .orElse(providers.environmentVariable("GOOGLE_WEB_CLIENT_ID")).getOrElse("")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("interviewtrainer-release.keystore")
            storeType = "PKCS12"
            storePassword = "InterviewTrainer-Release-2026!9K7vQ2mR"
            keyAlias = "interviewtrainer"
            keyPassword = storePassword
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL",
                "\"${providers.gradleProperty("DEBUG_API_BASE_URL").get()}\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "API_BASE_URL",
                "\"${providers.gradleProperty("RELEASE_API_BASE_URL").getOrElse("")}\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.credentials.core)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.google.play.billing.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)
    implementation(libs.hilt)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.retrofit.rxjava3)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.rxjava3)
    implementation(libs.rxandroid3)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}