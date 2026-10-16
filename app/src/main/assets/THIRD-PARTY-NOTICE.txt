# Bundled icon attribution

The Material launcher control set uses 21 Google Material Icons from https://github.com/google/material-design-icons, fetched from `src/*/materialicons/24px.svg` and converted to Android vector paths. Google distributes these under Apache License 2.0. The complete license and original SVGs are in `third-party/material-icons/`. The license and this notice are bundled in the APK's assets, accessible through Settings → about.

Retro and Wave are original launcher control sets. No icon fonts, emoji, Sony artwork, or Microsoft artwork are used for launcher controls. Third-party app icon packs are installed separately and retain their respective licenses; the launcher reads their resource mappings.

## Liquid glass

The rounded-rectangle lens math in `GlassSurface.Gpu` is adapted from Kyant's [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), commit `65ab177e90e5c1d8c62e70cf7755841982da65f6`, `backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt`. Copyright 2025 Kyant, Apache License 2.0. Modified for native Android Views, a shared stationary bitmap texture, uniform corners, restrained dispersion and edge lighting. The license is in `third-party/android-liquid-glass/LICENSE` and bundled in app assets.

## Smartspacer Client SDK

The optional Smartspacer top-zone item (Android 10+) uses the [Smartspacer](https://github.com/KieronQuinn/Smartspacer) Client SDK `com.kieronquinn.smartspacer:sdk-client:1.1.2` by Kieron Quinn, Apache License 2.0. The Smartspacer app itself (GPL-3.0) is installed separately and is not bundled. The SDK brings these Apache License 2.0 libraries: AndroidX (core, fragment, lifecycle, viewpager, constraintlayout and their dependencies), Kotlin standard library, kotlinx.coroutines, and skydoves Balloon. The Apache License 2.0 text is the same as the bundled Material Icons license.
