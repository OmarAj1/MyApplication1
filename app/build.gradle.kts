plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 36
    // Add this inside the 'android' block in app/build.gradle.kts
        tasks.register("buildReactApp", Exec::class) {
            workingDir = file("src/main/assets")
            // Use 'npm.cmd' on Windows, 'npm' on Mac/Linux
            commandLine(if (System.getProperty("os.name").toLowerCase().contains("windows")) "npm.cmd" else "npm", "run", "build")
        }

        tasks.named("preBuild") {
            dependsOn("buildReactApp")
        }
    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += ""
                // Enables 16 KB page size support for Android 15 compatibility
                arguments += listOf(
                    "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384",
                    "-DCMAKE_EXE_LINKER_FLAGS=-Wl,-z,max-page-size=16384"
                )
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
    packaging {
        resources {
            excludes += "org/bouncycastle/x509/CertPathReviewerMessages_de.properties"
            excludes += "org/bouncycastle/x509/CertPathReviewerMessages.properties"
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.libadb.android)
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    implementation("org.conscrypt:conscrypt-android:2.5.3") // Added
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81") // Added/Updated
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}