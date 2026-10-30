# Eigen Launcher — v1.0.0

A calm, native Android home screen: your apps in drawers, your messages on the left, your widgets on the right, and an assistant that runs on the phone. Android 9+ (API 28), built on Compose and Kelvin, 72F's design system. No internet permission, no ads, no analytics, no account.

## Install

Build a signed APK with `./scripts/release.sh` (see [Build](#build)) and install it. Open **Eigen Launcher**: a short tour sets it as Home, picks a colour and a clock, shows the gestures, and offers a wallpaper and notification access. Skip it anytime; replay it from Settings. Android's Home app setting switches back anytime.

Eigen is a new app (`com.studio72f.eigen`). It does not install over the 72F Launcher betas and RCs (`com.studio72f.freedom`); those builds stay in `dist/` for reference.

## Using it

- **Home.** The clock, then your drawers by name. Tap a drawer to open it into a grid of cards; several can be open at once. Pull down to open them all, pull again to close them.
- **App drawer.** Swipe up for every app, A to Z, with letter headers and a scroller.
- **Search.** The capsule at the bottom searches every launchable app, work profile included. Type a question instead and the assistant answers it.
- **Hold an app** for its shortcuts, Details (its dashboard), the dock, favourites, moving it to another drawer, renaming, hiding (it stays in search) and uninstalling.
- **Hold home** for Settings, wallpaper, clock and colour. **Hold the clock** to change its style.
- **The dock** holds up to four apps above the search capsule.
- **App dashboard.** Time today hour by hour (with usage access), storage, version, and how the app's card looks on home.
- **Swipe right** for Conversations and the Assistant. **Swipe left** for the widgets page.

## Widgets page

Eigen's own widgets (Now playing, Next alarm, Agenda, Day, Battery, Screen time, Focus timer, World clocks, Calculator, Notes) and any widget from your apps. They stack from the bottom, by the thumb. Hold one to drag it into a new place or onto Remove. App widgets use Android's bind prompt and the provider's own setup screen. The agenda asks for calendar access only when added.

## Contexts

What the widgets page shows, and which drawer home opens on, follow what you're doing. A context is a rule (days, a time window, headphones, charging, Do Not Disturb, media playing) and a result (a drawer and an ordered list of widgets). The first matching context wins; **everyday** is the fallback. Contexts are checked only while home is on screen; nothing runs in the background. Hold a context for an hour, three hours or until morning. See [the design notes](docs/CONTEXTS.md).

**Smartspacer** (Android 10+): add its item to a context to show At a Glance targets and complications. Install [Smartspacer](https://github.com/KieronQuinn/Smartspacer) separately; without it the item shows an install hint.

## Conversations and the assistant

**Conversations** gathers notifications from the messaging apps you choose into one timeline, with inline replies. It needs notification access and reads only what those notifications already show.

**The assistant** uses Gemini Nano through Android AICore where the phone has it, and quick on-device answers (open an app, the time, battery, a sum) everywhere else. The model runs in AICore on the phone; the conversation is kept in memory only.

## Look

Settings → Look: five colours (Kesar, Fire, Ladoo, Haldi, Greyscale), six clock styles, wallpaper (your photo, the phone's wallpaper, solid black, or one of Eigen's shader textures drawn in your colour), home blur, app icons (colour, monochrome or an ADW/Nova-compatible icon pack), text legibility over photos, Monochrome only, wallpaper brightness, and Glass: **Frost** (Eigen's default) or **Liquid glass**, each with its own intensity. Each card can be Frost, Stained or Lit, and wide.

**Text legibility.** **Auto** measures the photo's bright and dark spots after dimming. If text contrast would fall below 4.5:1 (WCAG AA) it uses **Strong**, otherwise **Subtle**. Over the phone wallpaper it uses Android's colour hints.

Photos come through Android's document picker (no photo-library permission), are stored privately, and decoded at most 2048 px. Liquid glass follows the effect stack of [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (Kyant, Apache-2.0).

## Eigen Plus

Eigen is a complete launcher for free. **Eigen Plus** is a one-time Play purchase (`eigen_plus`, non-consumable) that adds:

- unlimited contexts (free: everyday plus one);
- the Outline, Kesar split and Binary clocks;
- every texture (free: Ember and Silk);
- Stained and Lit cards, and wide cards;
- the Screen time, Focus timer, World clocks, Calculator and Notes widgets;
- Conversations.

Plus clocks and card styles can be tried on before buying. Gates sit where something is chosen, so anything already set up keeps working. Installs from before Plus existed keep it for good (`plus_grandfathered`). Ownership comes from Play's purchase record on the phone, with no account or server. Plus survives a backup restore but is never written into a backup. Tips under Support Eigen unlock nothing. The rules live in `ui/Plus.kt`.

## Privacy

Eigen requests no internet permission. ML Kit brings Google's telemetry uploader, which asks for it; the manifest removes the permission, so those uploads fail on the phone. Play Billing talks to the Play Store app on the phone. Every other permission is optional and asked for when a feature needs it: notification access (Conversations, dots, Now playing), calendar (Agenda), usage access (screen time) and Smartspacer.

## Backup and restore

Settings → Backup and restore exports a `.72fbackup.zip` with drawers, favourites, hidden apps, labels, contexts, the look and the saved photo. Restore validates the whole package, shows what it will replace, then applies it. Apps, icon packs, Android permissions and the Home role must be set up on the new phone separately. Keep backups somewhere safe: they include your photo and how you organise your apps.

## Build

Use JDK 21 and Android SDK 37 / Build Tools 37.0.0, with Android Gradle Plugin 9.4.1 and Gradle 9.8.2. Set `ANDROID_HOME` or `sdk.dir` in untracked `local.properties`.

```sh
./gradlew :app:assembleDebug :app:lintDebug
./scripts/test.sh
```

`./scripts/test.sh` runs the JVM tests (categories, context rules, legibility) with `JAVA_HOME` or Android Studio's Java.

On an emulator (both clear or change Eigen's data, so never point them at a phone you use; set `ANDROID_SERIAL` to pick the device):

- `./scripts/engine-test.sh` installs the build, a test icon pack and the instrumentation checks: themes, backups, restore, glass and icon packs on real Android APIs.
- `./scripts/eigen-smoke.py` drives the UI: the tour, both side pages, the clock sheet, widgets, contexts, the app dashboard and every Plus gate, including try-on and an upgrade from a pre-Plus install. Run it against a debug or `qa` build (R8, but debuggable).

Release APK (R8-shrunk), signed and checksummed into `dist/`:

```sh
./scripts/release.sh
```

Create the production key first: run `./scripts/make-release-key.sh` yourself. It asks for passwords and writes an untracked `keystore.properties`, after which release builds sign with it. Back up the keystore and its passwords: Play and Android only accept updates signed with the same key. Without it, `release.sh` signs with the debug key and warns.

CI (`.github/workflows/ci.yml`) runs the JVM tests, lint and every build type on push and pull request.

## Translations

Only English ships. Older screens keep their text in `app/src/main/res/values/strings.xml` (whole phrases as format strings; context-rule words in `rule_words` and `rule_days`); `scripts/extract-strings.py` moves new Java literals there. Most Kelvin UI text is still in the Kotlin source and needs extracting before translation.

## Implementation

- `MainActivity`: lifecycle, app discovery through LauncherApps and profiles, settings, dialogs, widget hosting and persistence. It owns the data; `ui/EigenState` carries it to Compose, and `EigenHost` is how the UI asks for changes.
- `ui/`: the Compose UI on Kelvin (`design/kelvin`): home, drawers and dock (`Home.kt`), the side pages, the widgets page, the app dashboard, sheets, settings, the tour, and Plus.
- `ai/`: the assistant, through ML Kit's GenAI Prompt API on AICore, with on-device quick answers.
- `EigenBilling`: Play Billing for Plus and tips.
- `CategoryRules`: local, heuristic app categorisation and merges; manual moves win.
- `ContextRules` / `ContextStore`: first-match rule evaluation (JVM-tested) and JSON persistence.
- `ThemeEngine`, `BackupStore`, `IconPacks`: bounded, data-only theme data, validated backups and icon-pack mapping.
- `MediaAccessService`: the notification listener behind Conversations, dots and media sessions. For dots it reads only the posting package, user and flags.

## Reference boundary

Eigen's interaction model was studied from Ratio 7.2.0 by using it ([teardown](docs/RATIO_TEARDOWN.md)); nothing was decompiled or copied. Earlier 72F builds studied GraceLauncher (GPL-3.0) and focus-launcher for motion and layout numbers, reimplemented independently. No source, assets or datasets were imported.

## Known limits

Categorisation is heuristic and will need corrections for niche apps. Work profile availability depends on Android policy. The assistant's language model needs a phone with AICore. Eigen widgets follow the theme; app widgets keep their own colours on Android 16+. Version 1.0.0 was verified on an Android 17 emulator: physical phones, OEM gesture navigation, Android 9–16, TalkBack, live media sessions, headphones and real Smartspacer cards still need testing. See [the release notes](docs/1.0.0-NOTES.md).
