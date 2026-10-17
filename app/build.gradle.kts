import java.util.Properties

plugins { id("com.android.application") }

// Production signing: create it with scripts/make-release-key.sh. keystore.properties and the .jks stay
// out of git; without them, release builds are unsigned and scripts/release.sh signs with the dev key.
val releaseKey = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
    namespace = "dev.seventwo.launcher"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.seventwo.launcher"
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "dev.seventwo.launcher.EngineChecks"
        versionCode = 8
        versionName = "1.0.0-rc5"
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
}
dependencies {
    // Optional Smartspacer top-zone item. The SDK needs API 29+; every call site is guarded.
    // Smartspacer Client SDK (Apache-2.0); 1.2 needs AGP 9.1+ and compileSdk 37.
    implementation("com.kieronquinn.smartspacer:sdk-client:1.2")
    implementation("androidx.viewpager:viewpager:1.1.0") // referenced directly by layout-v29/smartspacer_view.xml
}
