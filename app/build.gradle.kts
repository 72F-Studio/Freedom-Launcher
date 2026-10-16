plugins { id("com.android.application") }
android {
    namespace = "dev.seventwo.launcher"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.seventwo.launcher"
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "dev.seventwo.launcher.EngineChecks"
        versionCode = 3
        versionName = "1.0.0-beta3"
    }
    buildTypes {
        release {
            // R8 keeps the Smartspacer SDK's AndroidX/Kotlin footprint small.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
