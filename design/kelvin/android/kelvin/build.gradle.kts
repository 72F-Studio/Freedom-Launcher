plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "studio72f.kelvin"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig {
        minSdk = 24
        // Kelvin compiles against API 37.2 for the platform backdrop (NativeBackdrop.kt), but only
        // calls it after a runtime check, so apps can keep compiling against plain API 37.
        aarMetadata { minCompileSdk { version = release(37) } }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// Kelvin depends on AndroidX Compose foundation and UI, on Backdrop (Kyant, glass rendering) and
// on Vitro (itshedimisawi, the iOS-calibrated Vitro texture), all Apache 2.0. No Material, no Miuix.
dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kyant.backdrop)
    implementation(libs.vitro)
}
