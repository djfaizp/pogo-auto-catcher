plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.catcher.pogoauto"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.catcher.pogoauto"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Use environment variables for CI/CD or gradle.properties for local builds
            val keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: project.findProperty("signing.keyAlias") as String?
            val keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: project.findProperty("signing.keyPassword") as String?
            val storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: project.findProperty("signing.storePassword") as String?
            val storeFile = System.getenv("SIGNING_STORE_FILE") ?: project.findProperty("signing.storeFile") as String?

            if (keyAlias != null && keyPassword != null && storePassword != null) {
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                this.storePassword = storePassword

                // Look for the keystore file in multiple possible locations
                val possibleLocations = listOf(
                    "../keystore.jks",  // Root project directory
                    "keystore.jks",     // App module directory
                    "${rootProject.projectDir}/keystore.jks"  // Absolute path to root project directory
                )

                for (location in possibleLocations) {
                    val keystoreFile = file(location)
                    if (keystoreFile.exists()) {
                        this.storeFile = keystoreFile
                        println("Using keystore at: ${keystoreFile.absolutePath}")
                        break
                    }
                }

                // If no keystore was found, default to the root project directory
                if (this.storeFile == null || !this.storeFile!!.exists()) {
                    this.storeFile = file("../keystore.jks")
                    println("No keystore found, defaulting to: ${this.storeFile!!.absolutePath}")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // Configure native library packaging
    packagingOptions {
        jniLibs {
            keepDebugSymbols += "**/libfrida-gadget.so"
            useLegacyPackaging = true  // Use legacy packaging for better compatibility
        }
    }

    // Ensure native libraries are properly extracted
    androidResources {
        noCompress += "so"  // Don't compress .so files
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}