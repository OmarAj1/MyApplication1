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
            // This excludes the specific duplicate files causing the crash
            excludes += "org/bouncycastle/x509/CertPathReviewerMessages_de.properties"
            excludes += "org/bouncycastle/x509/CertPathReviewerMessages.properties"
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)

    // --- ADB Dependencies ---

    // --- ADB & Security Dependencies ---
    implementation(libs.libadb.android)
    implementation("org.conscrypt:conscrypt-android:2.5.3")

    // --- UPDATE THIS LINE ---
    // Changed from 'jdk15on:1.70' to 'jdk15to18:1.81' to match your project's other libs
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}