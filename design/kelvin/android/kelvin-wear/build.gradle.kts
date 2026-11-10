plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Kelvin for Wear OS: Kelvin's colours, type and Flat finish on a round screen. The list that scales toward the
// edges and the curved clock come from AndroidX Wear Compose Foundation (Apache 2.0). No Wear Material.
android {
    namespace = "studio72f.kelvin.wear"
    compileSdk { version = release(37) }
    // Wear OS 3, the first with the current tiles and complications.
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":kelvin"))
    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.core.ktx)
}
