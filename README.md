# M-DOS DeX

Android project containing separate Phone and Android TV variants. The current build packages the existing M-DOS 4.3 HTML desktop inside an Android WebView.

## Build

GitHub Actions builds both debug APKs on every push to `main` and on manual workflow dispatch.

- Phone: `app/build/outputs/apk/phone/debug/`
- Android TV: `app/build/outputs/apk/tv/debug/`

## Important status

This is the initial APK packaging baseline. It embeds the M-DOS 4.3 interface; LAN phone-to-TV control, native Android app streaming/virtual-display support, app discovery, and LLMPK runtime integration are not implemented yet. Debug APKs are for testing and are not signed production releases.
