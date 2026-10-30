import java.util.Properties

plugins { id("com.android.application"); kotlin("plugin.compose") }

// Production signing: create it with scripts/make-release-key.sh. keystore.properties and the .jks stay
// out of git; without them, release builds are unsigned and scripts/release.sh signs with the dev key.
val releaseKey = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
    namespace = "com.studio72f.eigen"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.studio72f.eigen"
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "com.studio72f.eigen.EngineChecks"
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        if (releaseKey != null) create("release") {
            storeFile = rootProject.file(releaseKey.getProperty("storeFile"))
            storePassword = releaseKey.getProperty("storePassword")
            keyAlias = releaseKey.getProperty("keyAlias")
            keyPassword = releaseKey.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            if (releaseKey != null) signingConfig = signingConfigs.getByName("release")
            // R8 keeps the Smartspacer SDK's AndroidX/Kotlin footprint small.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Release code (R8 and resource shrinking), but debuggable so UI suites can read prefs with run-as.
        create("qa") {
            initWith(getByName("release"))
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
dependencies {
    // Optional Smartspacer top-zone item. The SDK needs API 29+; every call site is guarded.
    // Smartspacer Client SDK (Apache-2.0); 1.2 needs AGP 9.1+ and compileSdk 37.
    implementation("com.kieronquinn.smartspacer:sdk-client:1.2")
    implementation("androidx.viewpager:viewpager:1.1.0") // referenced directly by layout-v29/smartspacer_view.xml
    implementation("com.android.billingclient:billing:9.1.0")

    // UI: Kelvin (72F design system) on Compose. Home, the side pages, search and the assistant are Compose.
    implementation(project(":kelvin"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // On-device AI: Gemini Nano through Android AICore, via ML Kit's GenAI Prompt API (the engine Tofu uses).
    // AICore downloads and runs the model itself; Eigen has no internet permission.
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta2")
}
