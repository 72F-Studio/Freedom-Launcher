# 1.0.0-rc4: final release candidate

Release candidate 4, version code 7. **Feature complete:** from here on, only fixes go in before 1.0.0. It includes everything in [RC3](RC3-NOTES.md) and the settings/chrome redesign that followed it, plus the features below. It is signed with the same development key as every earlier build, so it installs over them and keeps launcher data.

## New

- **App shortcuts.** Holding an app shows its own shortcuts first (manifest, dynamic and pinned, up to five), the way Pixel and Niagara do. Android only allows this for the default launcher.
- **Rename apps.** Custom labels apply in lists and search. Search also matches the original name, and "original name" restores it.
- **Hide apps.** Hidden apps leave favorites, categories and all apps but stay findable in search. Settings → hidden apps brings them back.
- **Notification dots.** An accent dot sits next to apps with notifications. Ongoing, foreground-service and group-summary notifications don't count. It uses the existing notification access, and reads only which package posted (plus its user and flags), never titles, text or extras. Toggle it in Settings → home & categories.
- **Category order.** Reorder categories in settings; favorites stays first and all apps last. "Reset to default order" undoes it.
- **Android 16 (SDK 36).** The app compiles and targets SDK 36, as the Play Store now requires. Back moved to `OnBackInvokedCallback` (predictive back) on Android 13+; Android 9–12 still use `onBackPressed`.
- **Backups and copy.** Hidden apps, custom names, category order and the dots setting are backed up and validated on restore. The tour and the access screen mention shortcuts, rename, hide and dots.

## Verification

Android 17 emulator, x86_64.

- Lint: 0 errors at SDK 36.
- JVM: 10 category, 17 context-rule and 10 legibility checks.
- On-device engine checks: 115, including rejection of malformed hidden/label/order/dots backup values.
- UI suites on a fresh R8 `qa` build, each starting from cleared app data: `smoke.py`, `extended-smoke.py`, `features-smoke.py`, `wallpaper-smoke.py`, `a11y-smoke.py` and `theme-smoke.py` all pass in one run. The phone emulator process exited mid-gate once (adb lost the device; cause unknown), so it was restarted and all six were rerun.
- The new `features-smoke.py` covers:
  - predictive back;
  - Chrome's "New tab" shortcut in its menu;
  - rename and restore;
  - hide, find in search, unhide;
  - category order;
  - a notification dot on Messages from a real SMS sent to the emulator.

## Known limits

- Settings and labels are English-only; strings are not yet Android resources.
- The build is signed with the development key. A store release needs a production key, and moving to it means a reinstall: a different key can't install over this one.
- Custom folders are flat categories.
- App widget IDs are device-local, so a restored backup skips widgets from another phone.
- Work-profile availability depends on device policy.

## Release gate for 1.0.0

These haven't been tested; all checks so far ran on one Android 17 emulator:

1. Android 9–16, especially the API 29+ guards (Smartspacer) and the Android 12+ glass and color paths.
2. Physical devices and OEM skins: gesture navigation, battery management, the launcher role, and the glass pipeline on real GPUs.
3. Headphones, live media, the agenda with a signed-in calendar, and Smartspacer cards.
4. TalkBack in real use.
