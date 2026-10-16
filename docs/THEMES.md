# Theme creator guide — schema 2

Open Settings → appearance & themes → import theme. Plain `.json` files support colors, metrics, layout, and effects. A `.72ftheme` file is a normal ZIP with `theme.json` at its root plus referenced local assets. Exports use `.72ftheme.zip` so Android file providers recognize the archive; both ZIP and `.72ftheme` filenames import. Export theme package captures the active look, icon mode, icon-pack reference, and selected imported photo. It does not include app organization; use backup & restore for that.

Ready-to-import examples: `themes/wave.72ftheme` and `themes/liquid-retro.72ftheme`. These are original artwork and layout implementations, without Sony logos or proprietary assets.

```json
{
  "version": 2,
  "name": "my frosted theme",
  "layout": "metro",
  "uiIconSet": "retro",
  "backdrop": "glow",
  "background": "#06151D",
  "text": "#FFFFFF",
  "muted": "#ACBDCE",
  "accent": "#39B9D4",
  "font": "sans-serif-light",
  "titleSize": 60,
  "appSize": 27,
  "rowHeight": 62,
  "iconSize": 30,
  "iconStyle": "mono",
  "motionMs": 230,
  "animated": true,
  "blur": 18,
  "glass": 50,
  "liquid": true,
  "wallpaper": "assets/background.jpg",
  "icons": {
    "com.android.settings": "assets/settings.png"
  }
}
```

For that example, include `assets/background.jpg` and `assets/settings.png` in the ZIP. Remove asset fields when making a plain JSON theme. Custom fonts use `"fontAsset": "assets/typeface.ttf"` (TTF/OTF); include a font you have permission to distribute.

| Field | Supported values |
| --- | --- |
| `version` | 1 or 2; exports normalize to 2 |
| `name` | Up to 80 characters |
| `layout` | `metro` (large text strip) or `xmb` (category icons above labels) |
| `backdrop` | `glow`, `solid`, `waves`; a selected photo/system wallpaper replaces this |
| `background`, `text`, `muted`, `accent` | Opaque Android color strings such as `#071626` |
| `font` | `sans-serif-light`, `sans-serif`, `sans-serif-thin`, `sans-serif-condensed` |
| `fontAsset` | Packaged TTF/OTF; overrides `font` |
| `titleSize`, `appSize` | 24–80sp, 18–36sp |
| `rowHeight`, `iconSize` | 48–96dp, 20–48dp |
| `motionMs` | 0–600ms category transition |
| `animated` | Boolean; waves also respect disabled system animations |
| `uiIconSet` | `retro`, `wave`, `material`; default follows layout |
| `uiIcons` | Optional object mapping launcher control names to packaged PNG/JPEG/WebP images |
| `iconStyle` | `mono`, `color`, `none` |
| `icons` | Object mapping `package/activity` or package name to a local image |
| `iconPack` | Installed ADW-compatible package name; applications are not bundled |
| `wallpaper` | Packaged PNG/JPEG/WebP; importing it selects photo mode |
| `wallpaperMode` | `solid`, `system`, `photo`; photo needs a saved/imported photo |
| `blur` | 0–40dp backdrop blur |
| `glass` | 0–90 surface strength; 0 disables glass |
| `liquid` | Boolean; brighter edges and softer glass corners |

Missing fields use defaults. Text needs contrast of at least 4.5:1 against the background; muted/accent colors need 3:1. User wallpaper can affect actual contrast, so adjust wallpaper dimming for your photo.

Launcher icon names: `favorites`, `social`, `music`, `video`, `photos`, `games`, `travel`, `tools`, `all apps`, `folder`, `search`, `close`, `more`, `arrow`, `chevron`, `back`, `play`, `pause`, `previous`, `next`, `settings`. Custom categories use the folder icon. For example, `"uiIcons": {"search": "assets/search.png"}` overrides the current set; images are tinted to the theme color. Every built-in mark is a native vector, including contextual arrows and close buttons. Google Material Icons attribution/license are bundled under Settings → about → open-source notices.

App icon priority: packaged component icon → packaged package icon → selected installed icon pack → original app icon. Mono/color/none then controls presentation. ADW/Nova-style `appfilter.xml` mappings in resources or assets are supported. Adaptive icon masking, generated missing icons, dynamic calendars, and every vendor-specific icon-pack extension are not implemented. Package resources are read without executing third-party code. Missing packs on another phone fall back to original icons. [Lawnchair's icon-pack format overview](https://github.com/LawnchairLauncher/docs/blob/main/developers/concepts/icon-packs.md) describes the common mapping convention.

Blur is applied to the background layer, keeping labels and controls sharp. Android 12+ uses native RenderEffect; Android 9–11 software-blurs imported photos at a bounded resolution. Animated waves/glow are not software-blurred on those older versions. Live system-wallpaper blur is available only where Android's compositor supports it and may be disabled by the OS. Glass surfaces capture their portion of the app-owned backdrop. Liquid glass uses rounded-rectangle edge refraction, local blur, dispersion, and rim light in an original AGSL shader on Android 13+. Older devices use local software blur and bitmap-mesh refraction. Captures are bounded to 480px width; animated wave capture updates at roughly 6fps, while controls stay sharp. Live system wallpaper is outside the capture layer: select an image inside the launcher for actual photo refraction. No screenshot or broad storage permission is needed.

Packages are data-only: no scripts, downloaded resources, native libraries, or APK installation. Imports reject traversal, duplicate paths, unknown schema versions, unsupported assets, missing assets, and oversized content. Limits: 48 MiB expanded package, 32 MiB per entry, 1,200 entries, 256 KiB manifest, 1,000 icon mappings, wallpaper dimensions up to 8,192 per side, icon dimensions up to 2,048 per side. Exported packages include referenced custom assets; system wallpaper and installed icon packs remain references.

Backup archives use a separate version 1 manifest (`backup.json`, kind `72f-backup`), `theme/theme.json`, theme assets under `theme/`, and optional `wallpaper.jpg`. Import is staged before preferences change, and restore requires a summary confirmation. App identities include user/profile IDs; work-profile IDs can differ across phones and may require reassignment. Android apps, permissions, roles, and media sessions are outside the archive.
