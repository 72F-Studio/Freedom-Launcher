plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

// Kelvin is one library for Android and for desktop (Linux and Windows, on the JVM). What every platform shares is in
// commonMain; what only Android can do (glass, shaders, haptics, the system's settings) is in androidMain, and desktop
// has its own plain answers in desktopMain.
kotlin {
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    android {
        namespace = "studio72f.kelvin"
        // Kelvin compiles against API 37.2 for the platform backdrop (NativeBackdrop.kt), but only
        // calls it after a runtime check, so apps can keep compiling against plain API 37.
        compileSdk { version = release(37) { minorApiLevel = 2 } }
        minSdk = 24
        aarMetadata { minCompileSdk { version = release(37) } }
        androidResources.enable = true
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }

    jvm("desktop") {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.jetbrains.compose.runtime)
            api(libs.jetbrains.compose.foundation)
            api(libs.jetbrains.compose.ui)
        }
        // Android adds Backdrop (Kyant, glass rendering) and Vitro (itshedimisawi, the iOS-calibrated Vitro texture),
        // both Apache 2.0. No Material, no Miuix.
        // Desktop takes the same fonts and doodles Android ships as resources, straight from Android's folders, so
        // there is one copy of each. The variable fonts are Android's; desktop loads the static weights.
        named("desktopMain") {
            resources.srcDir("src/androidMain/res")
            resources.include("font/*_?00.ttf", "raw/*.txt")
        }
        // Draws Kelvin without a screen, to compare variants side by side (CornersRenderTest).
        named("desktopTest").dependencies {
            implementation(kotlin("test"))
            implementation(compose.desktop.currentOs)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            api(libs.androidx.compose.foundation)
            api(libs.androidx.compose.ui)
            implementation(libs.androidx.compose.ui.graphics)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kyant.backdrop)
            implementation(libs.vitro)
        }
    }
}

// `-Dkelvin.screenshots=<dir>` saves the off-screen renders there (CornersRenderTest).
tasks.withType<Test>().configureEach {
    System.getProperty("kelvin.screenshots")?.let { systemProperty("kelvin.screenshots", it) }
}
