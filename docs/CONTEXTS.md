# Contexts & settings — design

## Goal

The top zone (header, 10–40% of the screen) and the starting category follow what you're doing: calendar and alarm in the morning, media when earbuds connect, work apps during work hours. 72F owns the surface, so this needs no automation engine, no background service and no broad permissions.

## Model

A **context** is a named rule:

| Field | Meaning |
|---|---|
| `name` | Shown in settings and the "active because…" line |
| `enabled` | Disabled contexts never match |
| `days` | Bitmask, Monday = bit 0 … Sunday = bit 6. 0 = any day |
| `start`, `end` | Minutes after midnight. `start == end` = all day. `end < start` wraps past midnight |
| `headphones`, `charging`, `dnd`, `playing` | Optional requirements. Each is `any`, `yes` or `no` |
| `category` | Category to open on. Empty = keep the current behaviour (Favorites is Home) |
| `widgets` | Ordered top-zone items: `clock`, `alarm`, `agenda`, `media`, `widget:<appWidgetId>` |
| `header` | Top-zone height override: -1 = global setting, otherwise 0/10/20/30/40 |

Contexts are an ordered list. **The first enabled context whose conditions all match wins** (AND only — no OR, nesting or variables). A built-in **everyday** context is always last, has no conditions and cannot be deleted, so something always matches.

A **manual override** pins one context until a time (1 hour, until tomorrow 04:00, or until switched back to automatic).

## Signals

Evaluated when Home appears, every 10 s while it is visible, and shortly after a signal changes (1.5 s debounce, so flaky Bluetooth doesn't flicker the layout). Nothing runs while another app is in front.

| Signal | Source | Permission |
|---|---|---|
| Time / weekday | Clock | none |
| Headphones | `AudioManager` output devices (wired, USB, A2DP, BLE) | none |
| Charging | Sticky `ACTION_BATTERY_CHANGED` | none |
| Do Not Disturb / Modes | `NotificationManager.getCurrentInterruptionFilter()` | none |
| Media playing | Active media sessions | existing notification access (optional) |
| Agenda widget | `CalendarContract.Instances` | `READ_CALENDAR`, asked only when the agenda widget is added |

Wi-Fi network and location are deliberately excluded from v1.

## Applying a context

- **Top zone** rebuilds immediately with the context's widgets and height.
- **Category**: pressing Home opens the active context's category. When the context changes, the launcher only moves you if you are still on the previous context's starting category — it never yanks you away from somewhere you navigated to.
- **Why label**: the quick settings sheet shows the active context and the reason ("weekdays 07:00–09:00 · headphones"), plus override buttons.

## Third-party widgets

`AppWidgetHost` (host id 7272). Widgets are picked from an in-launcher list, bound through Android's bind prompt, configured when the provider requires it, and placed into a context's widget list. On Android 12+ hosted widgets receive the theme's colors through `setColorResources`, so Material You widgets follow the 72F palette. Widget ids are device-local: backups keep the reference but a missing widget is skipped silently on another phone.

## Settings

Smart Launcher-style two-stage sheet:

1. **Quick settings** (the "more" button or swiping up on the bottom bar): active context and override, theme swatches, toggle tiles (categories, icons, scrubber side, blur, header size), wallpaper dimming.
2. **Swipe the sheet up** for the full settings page: grouped sections with live summaries — contexts, home & categories, appearance, top zone, wallpaper, backup, about. Drag down (from the top of the page) or Back to collapse.

The context editor is a page inside the sheet: day chips, a time window, condition chips (any / yes / no), a category chip row, and the widget list with add/remove/reorder.

## Smartspacer

The `smartspace` item inflates the official Client SDK's `BcSmartspaceView` (`res/layout-v29/smartspacer_view.xml`), tinted with the theme's text color. It's offered and inflated only on API 29+, and shows an install hint when Smartspacer isn't installed. The SDK manages its own session; `SmartspacerClient.close()` runs in `onDestroy`.

The build uses SDK 1.2 (it needs AGP 9.1+ and compileSdk 37). Release builds enable R8 (`app/proguard-rules.pro` keeps the SDK), cutting the APK from about 9 MB to 1.6 MB.
