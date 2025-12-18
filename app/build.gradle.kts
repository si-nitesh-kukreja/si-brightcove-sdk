plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
}

android {
    namespace = "com.si.brightcove.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36
//        versionCode = 1
//        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
//            isShrinkResources = true
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
        compose = true
        buildConfig = true
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.si.brightcoveSDK"
            artifactId = "brightcove-sdk"
            version = "1.0.0"
            // Use the Android release variant AAR
            afterEvaluate {
                from(components["release"])
            }
        }
    }
//    repositories {
//        maven {
//            url = uri("https://your.repo.url/repository/releases") // replace with your repo URL
//            credentials {
//                 Change these values or set via gradle.properties / env vars
//                username = (findProperty("mavenUser") as String?) ?: System.getenv("MAVEN_USER") ?: "mavenUser"
//                password = (findProperty("mavenPassword") as String?) ?: System.getenv("MAVEN_PASSWORD") ?: "mavenPassword"
//            }
//        }
//    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Lifecycle for ViewModel
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Brightcove SDK
    implementation(libs.android.sdk)

    // ExoPlayer for video playback
    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.ui)

    // Coil for image loading
    implementation(libs.coil.compose)

    // Gson for JSON parsing
    implementation(libs.gson)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}