# M-DOS DeX + Link to M-DOS

This Android project builds two different applications:

- **M-DOS DeX (TV)** — a standalone M-DOS environment. It does not need the phone app to start. The built-in **Phone Link** app displays a pairing QR code and starts a local Wi-Fi control server.
- **Link to M-DOS (Phone)** — a separate Android companion with a yellow launcher icon, QR camera scanner, full-screen touchpad, keyboard/D-pad controls, LLMPK transfer and file transfer.

## Phone Link capabilities

- QR pairing on the same Wi-Fi network using a temporary random token.
- Remote mouse movement, left/right click, scrolling, keyboard keys and text input.
- Transfer LLMPK packages to M-DOS; valid packages are installed into the existing M-DOS LLMPK runtime and launched.
- List and remove transferred LLMPK packages from the phone.
- Send arbitrary files, photos and videos to the M-DOS app's private Phone Link storage; M-DOS can preview supported image/video formats and import smaller files into its virtual file system.

## Current limitations

- Both devices must stay on the same local Wi-Fi network and the M-DOS TV app must remain open. This is LAN-only, not an internet remote-access service.
- LLMPK is the M-DOS web-app package format; it does not install arbitrary Android APKs.
- Large media files are stored in the M-DOS app's private Phone Link vault and can be previewed there. Import into the browser-based virtual file system is limited by browser storage.
- Android permissions are requested only when required (camera at scan time; file access through Android's system document picker).
- Debug APKs are test builds, not release-signed production packages.
