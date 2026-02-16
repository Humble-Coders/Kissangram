import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("com.google.gms.google-services")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.storage)
            implementation(libs.firebase.firestore)
            implementation(libs.datastore.preferences)
            implementation(("com.google.firebase:firebase-bom:34.9.0"))
            // Image loading
            implementation("io.coil-kt:coil-compose:2.5.0")
            
            // Note: Using LazyRow for carousel instead of foundation-pager for better compatibility
            
            // Swipe to refresh
            implementation("com.google.accompanist:accompanist-swiperefresh:0.32.0")
            implementation("com.google.android.gms:play-services-location:21.3.0")
            implementation("com.cloudinary:cloudinary-android:3.1.2")
            
            // Navigation Compose
            implementation("androidx.navigation:navigation-compose:2.8.0")
            
            // Media3 for video playback and transformation
            implementation("androidx.media3:media3-exoplayer:1.2.0")
            implementation("androidx.media3:media3-ui:1.2.0")
            implementation("androidx.media3:media3-transformer:1.2.0")
            implementation("androidx.media3:media3-common:1.2.0")
            
            // CameraX for video recording
            implementation("androidx.camera:camera-core:1.3.1")
            implementation("androidx.camera:camera-camera2:1.3.1")
            implementation("androidx.camera:camera-video:1.3.1")
            implementation("androidx.camera:camera-lifecycle:1.3.1")
            implementation("androidx.camera:camera-view:1.3.1")
            
            // Lottie for animations
            implementation("com.airbnb.android:lottie-compose:6.7.1")


        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.kissangram"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kissangram"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

// Task to print SHA-256 hash for Firebase Phone Auth
tasks.register("printSha256") {
    doLast {
        val keystoreFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
        if (keystoreFile.exists()) {
            exec {
                commandLine(
                    "keytool",
                    "-list",
                    "-v",
                    "-keystore", keystoreFile.absolutePath,
                    "-alias", "androiddebugkey",
                    "-storepass", "android",
                    "-keypass", "android"
                )
            }
        } else {
            println("Debug keystore not found at: ${keystoreFile.absolutePath}")
            println("Run: ./gradlew signingReport")
        }
    }
}
