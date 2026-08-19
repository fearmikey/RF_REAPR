import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.reapr.kotlin.android)
    alias(libs.plugins.reapr.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.fearmikey.rf_reapr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fearmikey.rf_reapr"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.4.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    buildTypes {
        release {
            val keystoreProperties = Properties()
            val keystorePropertiesFile = rootProject.file("local.properties")
            if (keystorePropertiesFile.exists()) {
                keystorePropertiesFile.inputStream().use { stream ->
                    keystoreProperties.load(stream)
                }
            }

            if (keystoreProperties.containsKey("KEYSTORE_PATH")) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(keystoreProperties["KEYSTORE_PATH"] as String)
                    storePassword = keystoreProperties["KEYSTORE_PASSWORD"] as String
                    keyAlias = keystoreProperties["KEY_ALIAS"] as String
                    keyPassword = keystoreProperties["KEY_PASSWORD"] as String
                }
            }

            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation("androidx.compose.material:material-icons-extended")
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)

    // Location
    implementation(libs.play.services.location)

    // UI Utilities
    implementation(libs.coil.compose)
    implementation(libs.accompanist.permissions)
    implementation(libs.poi.ooxml)
    implementation(libs.jtransforms)
    implementation(libs.snmp4j)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}