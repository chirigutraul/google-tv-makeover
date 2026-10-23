import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "dev.raul.tvhome"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.raul.tvhome"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        // Personal settings live in local.properties (never committed). See local.properties.example.
        fun prop(key: String) = localProps.getProperty(key, "").trim()
        buildConfigField("String", "TWITCH_CLIENT_ID", "\"${prop("twitch.clientId")}\"")
        buildConfigField("String", "TWITCH_CLIENT_SECRET", "\"${prop("twitch.clientSecret")}\"")
        buildConfigField("String", "TWITCH_STREAMERS", "\"${prop("twitch.streamers")}\"")
        buildConfigField("String", "DOCK_APPS", "\"${prop("dock.apps")}\"")
    }

    buildTypes {
        release {
            // R8 shrinking/optimising makes Compose noticeably smoother on the TV's slow CPU.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so it installs over the test builds without uninstalling.
            signingConfig = signingConfigs.getByName("debug")
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
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
