plugins { id("com.android.application") }
android {
    namespace = "dev.seventwo.launcher"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.seventwo.launcher"
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "dev.seventwo.launcher.EngineChecks"
        versionCode = 5
        versionName = "1.0.0-rc2"
    }
    buildTypes {
        release {
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
    // 1.2 needs AGP 9.1 and compileSdk 37; 1.1.2 is the newest release that builds with this toolchain.
    implementation("com.kieronquinn.smartspacer:sdk-client:1.1.2")
    implementation("androidx.viewpager:viewpager:1.0.0") // referenced directly by layout-v29/smartspacer_view.xml
}
