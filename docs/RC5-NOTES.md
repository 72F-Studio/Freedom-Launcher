# 1.0.0-rc5: final release candidate

Release candidate 5, version code 8. Same features as [RC4](RC4-NOTES.md), which was feature complete. This RC fixes everything that could be fixed without manual testing. It's signed with the same development key as every earlier build, so it installs over them and keeps launcher data.

## Fixed since RC4

- **Translatable UI.**
  - 345 UI strings moved from Java into `res/values/strings.xml`. Concatenations became whole-phrase format strings (`hold %1$s`, `Auto picked %1$s for this wallpaper`), so translators see complete sentences.
  - Context-rule words and day names moved into `rule_words` and `rule_days`. The accessibility announcements in the settings sheet and widget stack moved too.
  - The date line is now a format resource, so a translation can reorder it.
  - Only English ships; adding a language means copying `strings.xml` to `values-<language>/`.
  - `scripts/extract-strings.py` does the extraction. It rewrites only literals in UI positions and leaves preference keys and data alone.
- **Toolchain and target.**
  - Android Gradle Plugin 9.4.1, Gradle 9.8.1, and compileSdk/targetSdk 37 (Android 17), with Build Tools 37.0.0.
  - This unblocked the Smartspacer Client SDK 1.2 (from 1.1.2) and ViewPager 1.1.0.
- **Lint: no issues** (was 29 warnings).
  - Custom touch views now call `performClick` (accessibility services can click them).
  - `@TargetApi` became `@RequiresApi`. Its stricter propagation surfaced one lens-effect call that relied on an implicit API check, now guarded explicitly.
  - AndroidX drawable loaders are used.
  - Intentional cases are suppressed with a written reason: icon-pack resources found by name, the synchronous restore rollback, programmatic-only views, observe-only touch listeners, the predictive-back manifest attribute, and lowercase button copy.
- **Release signing.**
  - `scripts/make-release-key.sh` creates a production key and an untracked `keystore.properties`. You run it yourself; it prompts for passwords.
  - Gradle signs release builds with that key when the file is present.
  - `scripts/release.sh` builds, signs, verifies and checksums. It falls back to the development key with a warning.
  - Verified once with a throwaway key, which was then deleted.
- **CI.** `.github/workflows/ci.yml` runs the JVM tests, lint and every build type (debug, qa, release, androidTest) on push and pull request.
- `scripts/test.sh` now defaults to JDK 21 instead of Android Studio's bundled JDK 25, which the previous Gradle (8.13) rejected.

## Verification

Android 17 emulator, x86_64, on the new toolchain.

- Lint: no issues. JVM: 10 category, 17 context-rule and 10 legibility checks.
- On-device engine checks: 115.
- UI suites on the R8 `qa` build, each from cleared app data: `smoke.py`, `extended-smoke.py`, `features-smoke.py`, `wallpaper-smoke.py`, `a11y-smoke.py` and `theme-smoke.py` all pass in one run. The English text is unchanged, so the suites' text-based lookups also check that extraction kept every string.
- The signed RC5 APK installs over the previous build and starts on Home without errors. It targets SDK 37 and is 1.46 MB (RC4: 1.66 MB).

## Still open

These can't be done from here:

- **Manual and device testing (the 1.0.0 gate):**
  - Android 9–16. Only Android TV 36 and phone 37 emulator images are installed.
  - Physical devices and OEM skins, including the glass pipeline on real GPUs.
  - Headphones, live media, a signed-in calendar and Smartspacer cards.
  - TalkBack in real use.
- **The production key** is yours to create, and moving existing installs from the development key needs backup → uninstall → restore.
- **Translations** beyond English.
