# M-DOS DeX Architecture

## Product boundary

M-DOS DeX is an **Android TV-hosted desktop**. The TV runs the desktop locally and remains usable when no phone is connected. The phone is a companion controller and file/package sender.

This differs from a desktop-host architecture in which a Windows or Linux computer runs ADB, starts Android processes, and embeds a screen-mirroring stream. M-DOS does not require a PC host, ADB host process, `app_process` payload, or scrcpy to start its desktop.

## Runtime layers

| Layer | TV edition | Phone edition |
|---|---|---|
| Presentation | Bundled WebView desktop; TV DPAD navigation and window layout | Native screens for discovery, touchpad, keyboard, apps and files |
| Runtime | `MainActivity`, WebView bridge, `PhoneLinkServer` | `WifiCompanionActivity`, input coalescer and connection monitor |
| Transport | Local HTTP server on TCP 8765 and DNS-SD advertisement | DNS-SD discovery and authenticated HTTP client |
| Feature modules | Remote input dispatch, file storage, LLMPK import/removal | Pointer/keyboard controls, file selection/upload, package listing/removal |
| Persistence | Trusted phone tokens, pairing code, received files and LLMPK packages | Paired TV identity, address and token |

The desktop UI is bundled in `app/src/main/assets/index.html`; it does not need a network connection to start.

## Phone Link lifecycle

1. The TV starts its desktop WebView and local Phone Link HTTP server.
2. The server advertises `_mdoslink._tcp.` using Android Network Service Discovery.
3. The phone discovers the TV, or accepts a manually entered LAN address.
4. First pairing uses the short code displayed by the TV. The server rotates that code after a successful pairing and issues a random 256-bit bearer token.
5. The phone stores the TV identity and token locally. Future connections reuse that trusted token.
6. The phone calls `GET /hello` to negotiate the protocol and discover supported capabilities. Older TV builds without `/hello` remain reachable through the legacy `/ping` fallback.
7. While connected, the phone checks the session every 10 seconds and reports a lost link without deleting saved pairing credentials.

## Protocol contract

The shared contract lives in `MdosLinkProtocol.kt`.

- Protocol ID: `mdos-phone-link`
- Current protocol version: `3`
- Minimum compatible version advertised by this build: `2`
- Capabilities: device discovery, one-time pairing, remote pointer, keyboard input, file transfer, LLMPK management and connection health.

Core routes:

| Route | Purpose |
|---|---|
| `GET /identify` | Public device identity and protocol metadata |
| `POST /pair` | One-time code exchange and trusted-token issuance |
| `GET /hello` | Authenticated version/capability handshake and server health |
| `GET /ping` | Backward-compatible connection check |
| `POST /input` | Remote pointer, click, scroll, key and text commands |
| `GET /files`, `GET /apps` | List received files and LLMPK packages |
| `POST /upload` | Upload a user-selected file or LLMPK package |
| `DELETE /files`, `DELETE /apps` | Remove a received file or package |
| `GET /file` | Read a received file |

When adding a feature, add its capability identifier to the shared contract and keep its route implementation in the TV runtime. The phone should check the negotiated capability before showing a feature as available.

## Reliability rules

- Keep desktop startup independent of phone pairing or Wi-Fi discovery.
- Never block the Android main thread with network, file-transfer or discovery work.
- Coalesce high-frequency pointer movement; send clicks and key events as discrete commands.
- Use bounded request bodies and safe file names for uploads.
- Keep pairing credentials out of UI text and URLs; send the trusted token in the `X-MDOS-Token` header.
- Stop discovery, heartbeat callbacks and sockets when their owning Activity is destroyed.

## Security boundary

Phone Link is designed for a trusted local network. Pairing is gated by a one-time code and subsequent requests require a random token. The current transport is plain HTTP on the LAN, not end-to-end encrypted; users should avoid untrusted or public Wi-Fi. Do not describe this token scheme as TLS or as an independent security audit.

## Planned module boundaries

The next clean separations are a desktop window manager, a typed command/event router, and optional device/media/notification integrations. These should remain independent modules so the TV desktop continues to work without the phone companion.
