// Kelvin, the 72F design system, built from the copy in design/kelvin (synced from
// github.com/72F-Studio/72F-Kelvin; the commit is in design/kelvin/SYNCED_FROM). Only this build file is Eigen's own.
plugins { id("com.android.library"); kotlin("plugin.compose") }
android {
    namespace = "studio72f.kelvin"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    sourceSets {
        getByName("main") {
            kotlin.directories += "../design/kelvin/android/kelvin/src/main/kotlin"
            res.directories += "../design/kelvin/android/kelvin/src/main/res"
        }
    }
}
// AndroidX Compose foundation and UI plus Backdrop (Kyant), all Apache 2.0.
dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("io.github.kyant0:backdrop:2.0.1")
}
