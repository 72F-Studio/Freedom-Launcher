plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Kelvin for screens still built from Views: Kelvin's colours, type, bar styles, glyphs and groups as Android
// resources and a RecyclerView decoration. The fonts come from :kelvin. AndroidX AppCompat (the bar styles' parents)
// and RecyclerView, both Apache 2.0. No Material.
android {
    namespace = "studio72f.kelvin.views"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 24 }
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
    api(libs.androidx.appcompat)
    api(libs.androidx.recyclerview)
    implementation(libs.androidx.core.ktx)
}
