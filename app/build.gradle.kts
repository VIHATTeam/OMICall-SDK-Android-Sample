plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "vn.vihat.omicall"
    compileSdk = 35

    defaultConfig {
        applicationId = "vn.vihat.omisample"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        checkReleaseBuilds = false
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.livedata)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.flexbox)
    implementation(libs.material.v190)
    implementation(kotlin("script-runtime"))

    implementation(libs.omi.sdk)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.logging.interceptor)
    implementation(libs.converter.gson)
    implementation(libs.retrofit)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)
}