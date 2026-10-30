# Blloc Ratio Launcher v7.2.0 (PREMIUM) — Complete Architectural Teardown & Design System Specification

> Reverse-engineered from package `com.bllocosn` (`Ratio Productivity v7.2.0 (PREMIUM) (2).apk`)  
> Target Project Reference: `Eigen-Launcher`

---

## 1. Executive Summary & Window / Decor Pipeline

### A. Manifest & Activity Architecture
- **Single Activity Core**: `com.bllocosn.MainActivity` hosts the entire launcher UI.
- **Window Translucency & Decor Flags**:
  - `Theme.RatioNativeBase` sets `android:windowShowWallpaper="true"`, `android:windowBackground="@android:color/transparent"`, and `android:windowTranslucentStatus="true"`.
  - Disables hardware decor fit via custom layout `com.blloc.uicomponents.view.KeyboardAvoidanceView`.
  - Custom system inset consumption via `ViewCompat.setOnApplyWindowInsetsListener` to route status bar and navigation bar padding down to the active fragment.

### B. ViewPager & Paging Architecture
- **Paging Container**: `com.bllocosn.MainActivityViewPager` extending `androidx.viewpager.widget.ViewPager`.
- **Adapter**: `p210jb.h` / `androidx.fragment.app.G` (`FragmentPagerAdapter`).
- **3-Panel Screen Indices**:
  - **Screen 0 (`Index 0`) — Root**: `L7.u` / `RootFragment` (Productivity cards, calculator, notes, weather, media player, external AppWidgets).
  - **Screen 1 (`Index 1`) — Tiles**: `com.blloc.kotlintiles.ui.TilesFragment` (Main home grid, category drawers, app search, fast scroll).
  - **Screen 2 (`Index 2`) — Tree**: `C5841b` (Jetpack Compose Tree) / `com.blloc.bllocjavatree.ui.TreeFragment` (Notification listener hub, unified messaging, Telegram TDLib).
- **Default Start Screen**: `viewPager.setCurrentItem(1, false)` (Tiles).
- **Edge Drag Physics**:
  - Handled via `p015ac.e` with touch interception delegator.
  - Programmatic page gliding via `viewPager.beginFakeDrag()` and `viewPager.fakeDragBy(xOffset)`.
  - Nested scroll handling: Screen 0 and Screen 1 scroll vertically (`RecyclerView`s); horizontal drag passes through to `ViewPager` only when `Math.abs(dx) > Math.abs(dy) * 1.5` and child recycler is either at top (`!canScrollVertically(-1)`) or touch is outside scrolling bounds.

---

## 2. Screen 0: Root Dashboard Subsystem

### A. Persistence Architecture (`FoundationDatabase`)
Room Database SQLite instance: `foundation_database`:
- **Tables**:
  - `widgets`: ID, card type, position index, height, collapsed state, update timestamp.
  - `notes`: Note ID, body text, updated timestamp, pinned state, formatting JSON.
  - `calculator`: Expression history, memory registers.
  - `weather`: Cached weather JSON, geolocation latitude/longitude, forecast array.
  - `cities`: World clock and timezone entities.
  - `articles`: News feed cache, publisher, thumbnail URI, source link.
  - `currency_quotes`: Fiat/crypto rates against USD base, last fetch timestamp.
  - `converters`: Active unit conversion categories and pair presets.

### B. Sub-Modules
1. **Math Evaluation Engine (`p338pa.d`)**:
   - Custom in-house Shunting-Yard parser converting infix mathematical expressions into Reverse Polish Notation (RPN), executing floating point stack evaluation with high precision (`BigDecimal`).
2. **Notes Engine (`Ha.a` / `JSBridge`)**:
   - Embedded local `WebView` running a bundled React application with Slate.js rich-text WYSIWYG editor.
   - Bidirectional communication via Android `@JavascriptInterface` bridge:
     - `postNote(String id, String content)`
     - `deleteNote(String id)`
     - `requestNoteState()`
3. **Media Control Player Card (`MediaPlayerCardViewModel`)**:
   - Piggybacks on `TreeNotificationListener`'s component token:
     ```java
     MediaSessionManager msm = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
     ComponentName nlsComponent = new ComponentName(context, TreeNotificationListener.class);
     List<MediaController> controllers = msm.getActiveSessions(nlsComponent);
     ```
   - Scrapes `MediaMetadata.METADATA_KEY_ALBUM_ART`, track title, artist name, and routes playback keys (`KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE`, etc.) via `MediaController.TransportControls`.
4. **Native AppWidget Host (`PinnedCardsContainerView`)**:
   - Native `AppWidgetHost` embedded alongside Ratio cards.
   - Dynamically binds external system widgets (`AppWidgetHostView`) selected via `WidgetSelectorViewModel`.

---

## 3. Screen 1: Tiles App Drawer & Grid Subsystem

### A. Layout & Adapter Topology
- **Recycler**: `com.blloc.kotlintiles.ui.TilesFragment` host `RecyclerView`.
- **Custom Layout Manager**: `MainListLayoutManager` (`GridLayoutManager` with span count 4).
- **Span Size Lookup (`SpanSizeLookup`)**:
  - `VIEW_TYPE_HEADER` (`Type 0` — Drawer Title & Accordion Toggle): Span Size **4** (full row).
  - `VIEW_TYPE_COMPACT_TILE` (`Type 1` — Standard 1x1 App Icon): Span Size **1** (1/4 row).
  - `VIEW_TYPE_EXPANDED_TILE` (`Type 2` — Wide 2x1 App Tile): Span Size **2** (1/2 row).

### B. Accordion Folding & Auto-Centering
- When a Drawer header is tapped:
  1. Room database updates `DrawerDto.isExpanded`.
  2. Adapter computes affected child positions: `notifyItemRangeInserted` or `notifyItemRangeRemoved`.
  3. Smooth scroll auto-centering via `C2936k` (`LinearSmoothScroller` with `SNAP_TO_START` and 40dp top offset) to keep the tapped drawer title visible below the status bar.

### C. Drag-To-Delete Physics (`DropToDeleteView`)
- Tile long-press initiates Android drag-and-drop (`View.startDragAndDrop`).
- Top-of-screen drop target (`DropToDeleteView`) measures 3 distinct zones based on finger Y:
  - **Zone 0 (Inactive)**: Outside threshold.
  - **Zone 1 (Hover)**: Vibrates (`HapticFeedbackConstants.CLOCK_TICK`), background transitions to red (`#FF3B30`).
  - **Zone 2 (Commit)**: `HapticFeedbackConstants.CONFIRM`, initiates uninstall intent or tile unpinning.

### D. Visual Tile Styles (`BlurTileView.java`)
Configurable per app via `TileColorChooser`:
1. `CLASSIC`: Neutral grey background tint matching theme (`dark_theme_tile_classic`).
2. `HIGHLIGHT_DARK`: Inverted dark tint.
3. `HIGHLIGHT_BRIGHT`: High-contrast bright tint.
4. `MARKER`: High-visibility accent `#FFFC33` (Ratio signature yellow) or user accent.

---

## 4. Screen 2: Tree Unified Messaging Subsystem

### A. Notification Scraping & In-Line Reply Injection
- **Service**: `com.blloc.bllocjavatree.services.TreeNotificationListener` (`NotificationListenerService`).
- **Quick Reply Builder**:
  ```java
  NotificationCompat.Action wearAction = findWearReplyAction(sbn.getNotification());
  RemoteInput remoteInput = wearAction.getRemoteInputs()[0];
  Intent fillInIntent = new Intent();
  Bundle bundle = new Bundle();
  bundle.putCharSequence(remoteInput.getResultKey(), replyText);
  RemoteInput.addResultsToIntent(new RemoteInput[]{remoteInput}, fillInIntent, bundle);
  wearAction.actionIntent.send(context, 0, fillInIntent);
  ```

### B. Embedded Telegram MTProto Native Client
- Integrates native C++ shared library `libtdjni.so` (17.5 MB TDLib build).
- Direct MTProto protocol communication enabling end-to-end messaging within Ratio without opening the Telegram application.

### C. Contact & Conversation Aggregator
- Room database merging entities from WhatsApp, Telegram, Signal, and SMS into unified conversation cards (`MergedEntity` + `MergedXConversation`).

---

## 5. Complete Design System & Design Tokens

### A. Color Palette

```
  0% ------------------------ Neutral Ramp ------------------------ 100%
[#0B0B0B]  [#161616]  [#212121]  [#2C2C2C]  [#3E3E3E]  [#7A7A7A]  [#F2F2F2]
 gray_100   gray_90    gray_80    gray_70    gray_60    gray_40    gray_10
```

- **Neutral Ramp**:
  - `gray_100`: `#0B0B0B` (Primary background - Dark)
  - `gray_90`: `#161616` (Card background - Dark)
  - `gray_80`: `#212121` (Card surface elevated)
  - `gray_70`: `#2C2C2C` (Tile surface default)
  - `gray_60`: `#3E3E3E` (Tile stroke / dividers)
  - `gray_40`: `#7A7A7A` (Secondary metadata text)
  - `gray_20`: `#D2D2D2` (Subheading text)
  - `gray_10`: `#F2F2F2` (Primary high-contrast text)
- **Signature Accent**:
  - `blloc_accent_yellow`: `#FFFC33` (Universal Ratio yellow)
- **4 Primary Themes**:
  - **Dark**: `#0B0B0B` background, `#2C2C2C` tiles, `#F2F2F2` text.
  - **Light**: `#FAFAFA` background, `#EBEBEB` tiles, `#111111` text.
  - **Focus**: High-contrast OLED pure black `#000000` with disabled wallpaper.
  - **Sun**: High-visibility inverted amber/yellow daylight mode.

### B. Corner Radii & Elevation Tokens
- Compact Tile: `10.0dp` (`R.dimen.tile_corner_radius`)
- Pill / Quick Search Box: `15.0dp` (`R.dimen.search_box_corner_radius`)
- Bottom Sheet / Dialog: `22.0dp` (`R.dimen.sheet_corner_radius`)
- Stroke Width: `1.0dp` hairline vector borders (`R.dimen.divider_stroke_width`)

### C. Typography Rules
- **Brand Font**: `Inter` font family (bundled in `res/font/inter_*.ttf`).
- **Headings**: `Inter 18pt Semi-Bold` / `Inter 18pt Oblique`.
- **Body / Tile Labels**: `Inter 13pt Regular`, letter-spacing `0.02em`.
- **Numbers / Metrics**: `Roboto Medium Numbers` with tabular digits (`tnum`) for consistent widths in calculator and clock cards.

---

## 6. Graphics, Render, & Blur Pipeline

### A. Singleton Blurred Wallpaper Texture (`com.blloc.blurview.a`)
Instead of executing real-time RenderScript / CPU blurs per individual tile (which causes severe frame drops in a 4-column grid), Ratio renders **one cached wallpaper texture**:
1. Downsamples wallpaper bitmap to **0.3x** resolution (30% scale).
2. Runs single-pass `ScriptIntrinsicBlur` with radius **25.0f** using `U8_4` RGBA color format.
3. Caches resulting bitmap in memory as a static singleton.

### B. Screen-Space Coordinate Sampling (`BlurView.java` & `BlurTileView.java`)
Every tile on the screen simply acts as a coordinate window into the master blurred bitmap:
```java
int[] location = new int[2];
getLocationOnScreen(location);
float xRatio = (float) location[0] / screenWidth;
float yRatio = (float) location[1] / screenHeight;
// Draws cropped slice of the pre-blurred wallpaper, offset by [xRatio, yRatio]
```
- Redraw updates throttled to a minimum **10ms debounce** during scrolling.

### C. System-Wide Monochrome (Daltonizer Engine)
Achieved through Android's Accessibility Daltonizer setting:
```java
// Requires WRITE_SECURE_SETTINGS granted via ADB or system privileges
Settings.Secure.putInt(contentResolver, "accessibility_display_daltonizer_enabled", 1);
Settings.Secure.putInt(contentResolver, "accessibility_display_daltonizer", 0); // 0 = Monochromacy (Greyscale)
```

---

## 7. Digital Wellbeing & Usage Limiter

### A. Non-Root Foreground App Tracking (`u6/b.java`)
Ratio inspects foreground usage without window scraping:
- Checks `AppOpsManager.checkOpNoThrow("android:get_usage_stats", ...)`
- Calls `UsageStatsManager.queryEvents(System.currentTimeMillis() - 3600000, System.currentTimeMillis())`.
- Detects the last event where `event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED (1)`.

### B. Scheduling Engine (`UsageSchedulerImpl.java`)
- Computes exact seconds until the nearest app limit or warning threshold.
- Enqueues `OneTimeWorkRequestBuilder<LimitCheckWorker>()` with unique name `"work_name_limit_checker"` and tag `"work_tag_limit_checker"`.
- When warning is reached (2 to 6 minutes remaining): Fires heads-up notification with action intents for `RemindLaterReceiver` and `CloseAppReceiver`.
- When limit is reached (`<= 0 minutes`): Launches `LimitDialogActivity` (`FLAG_ACTIVITY_NEW_TASK`), showing blocking BottomSheetDialogFragment `usagelimitreached.b`.
- "Close App" Trick (`CloseAppReceiver`): Launches Ratio Launcher's own intent (`pm.getLaunchIntentForPackage(getPackageName())`), which forces the foreground app into the background paused state.

---

## 8. Offline Auto-Categorization Pipeline

### A. Bundled Database
- Stored in `res/raw/categorizer_db.json` (4.5 MB).
- Contains **50,000 top Android packages** with official Google Play category tags.

### B. Drawer Mapping & Constraints
- Google Play categories are mapped into **11 canonical Ratio Drawer categories** (`RatioCategories.java`).
- Each drawer has a mapped vector icon index (Games: 0, Mobility: 2, Productivity: 5, Finance: 7, Lifestyle: 8, Entertainment: 13, Messaging: 15, Social/Other: 16, Creativity: 18).
- Heuristic constraint: `MAX_APPS_FOLDER = 8` apps per drawer on initial categorization to avoid visual clutter. Remaining apps are routed to `"App Box"`.

---

## 9. Biometric Drawer & App Locking

- Managed by `BiometricLockView` & `BlurTileView.setLocked(true)`.
- Replaces or overlays app icon with a Lottie fingerprint animation running at `speed = 2.0f`.
- Themed base and accent colors configured for each of the 4 themes.
- Invokes `androidx.biometric.BiometricPrompt` with prompt `"Unlock messages"` or drawer unlock, firing a callback upon biometric authentication completion.

---

## 10. Tree Auto-Responder Engine

- **Database**: SQLite `autoreply_db` (`AutoReplyDatabase_Impl`).
  - `AutoReplyEntity`: `id`, `affectedTagId`, `activeStart`, `activeUntil`, `autoReplyMessage`, `repeatedWeekdays`.
  - `RespondedConversationEntity`: `id`, `autoReplyId`, `conversationId`, `respondedTimestamp` (deduplication ledger so contacts are not repeatedly spammed within the same active window).
- **Alarm Dispatch**: `AlarmBroadcastReceiver` handles `action_destroy_auto_reply` and `action_destroy_focus_hour` to manage auto-reply scheduling.

---

## 11. App Customization & Web Shortcuts

- **PWA Pinning**: `WebShortcutHandlerActivity` intercepts `android.content.pm.action.CONFIRM_PIN_SHORTCUT` via `LauncherApps.PinItemRequest`, calls `.accept()`, and opens modal `C3791a` to select the destination Drawer.
- **Tile Styling**: `TileColorChooser` uses a custom vertical `VerticalStaticViewPager` with 4 pages (`CLASSIC`, `HIGHLIGHT_DARK`, `HIGHLIGHT_BRIGHT`, `MARKER`), live accent color binding (`#FFFC33`), and vertical swipe paging.

---

## 12. System Permissions & Privileges Architecture

| Capability | Permission / Mechanism | Implementation Details |
| :--- | :--- | :--- |
| **Double-Tap Lock** | `AccessibilityService` (`GLOBAL_ACTION_LOCK_SCREEN`) | Avoids Device Admin lock, preserving biometric fingerprint unlock. |
| **Media Player Scraper** | `NotificationListenerService` (`TreeNotificationListener`) | Scrapes active sessions via `MediaSessionManager.getActiveSessions()` using NLS token. |
| **Monochrome Screen** | `android.permission.WRITE_SECURE_SETTINGS` | Writes `Settings.Secure.putInt(..., "accessibility_display_daltonizer", 0)`. |
| **App Usage Limiter** | `android.permission.PACKAGE_USAGE_STATS` | Queries `UsageStatsManager.queryEvents` for `ACTIVITY_RESUMED` events. |
| **Lock Screen Overlay** | `android.permission.SYSTEM_ALERT_WINDOW` | Renders `LimitDialogActivity` and warning overlays over target apps. |
