plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// 72F AI Share: any 72F app that has a Gemma model runs it for the other 72F apps on the phone, so a model is
// downloaded and stored once. See README.md for the protocol and the trust rules.
android {
    namespace = "studio72f.aishare"
    compileSdk = 37
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures { aidl = true; buildConfig = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests { isReturnDefaultValues = true } }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // litertlm-android ships newer Kotlin metadata than this toolchain; only stable APIs are used.
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    api(libs.litertlm.android)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
}
