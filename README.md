# Nice Home

<img alt="Logo" src="fastlane/metadata/android/en-US/images/icon.png" width="120" />

A fork of [Fossify Launcher](https://github.com/FossifyOrg/Launcher) with the
**Graphite & Ice** treatment from [Nice Launcher](https://github.com/oakridge-rgb/NiceLauncher),
plus the feature that made that fork fun: the **daily wallpaper**.

## What's different from Fossify Launcher

| | Fossify Launcher | Nice Home |
| --- | --- | --- |
| Daily wallpaper | — | ✅ fresh wallpaper every day (ported from Olauncher's WallpaperWorker, light/dark aware, sets home + lock screen) |
| App name | Launcher | **Nice Home** |
| Install | `org.fossify.home` | `org.nice.home` (side-by-side install) |
| Version | 1.10.0 (16) | 1.10.0-nice.1 (17) |
| Icon | Fossify house | house glyph in the Nice ice gradient on graphite; all 17 color variants kept (they recolor the background) |
| Build | AGP 9.5.1 | **AGP 9.3.1** pinned (top of Code on the Go's supported range) + heap lowered to 2 GB so it compiles on-device |

Everything else is untouched Fossify Launcher: widgets, icon color variants,
hidden icons, app drawer, folder support. The wallpaper toggle lives in
**Settings → App drawer section → "Daily new wallpaper"**; scheduling is a 4-hour
WorkManager periodic job whose day-keyed URL lookup makes it effectively
once-daily, and it keeps working across reboots with no extra receiver.

## Build

```
./gradlew assembleFossDebug    # APK at app/build/outputs/apk/foss/debug/
```

Kotlin 2.3.10, KSP, Room, `org.fossify:commons:7.1.3` — all from upstream,
unchanged. Min heap: 2 GB (`org.gradle.jvmargs`).

## Credits & license

All launcher logic is the Fossify team's work; the wallpaper engine is
Tanuj's (Olauncher) — both GPLv3, and this fork keeps every credit intact.
