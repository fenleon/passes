# Passes

An app for the Light Phone III to store and show codes — boarding passes,
library cards, memberships, anything a QR code represents.

Built as a **real LightOS tool** with the [light-phone/light-sdk](https://github.com/lightphone/light-sdk)
tool plugin: launched from the LightOS toolbox, UI on the Light design system.
Single-module since 2026-08-18 — storage and barcode rendering run in-process
in the tool; there is no companion APK. Modeled on the SDK's Authenticator
example.

Forked from [vandamd/passes](https://github.com/vandamd/passes) (MIT).

**Current release: 0.4.0** (2026-08-19) — single-APK tool; stacked codes with a
persistent `+`, delete with a confirm panel, fullscreen 1:1 barcode, calm
code-entry keyboard.

## Features

- Store a code by **scanning** it with the camera (QR, Aztec, PDF417,
  Data Matrix, Code 128, EAN/UPC) or by **typing** it on the LP3 keyboard
  (typed codes are stored as QR).
- **Stack codes** under one name — the `+` in the bottom-right adds another
  code to an existing pass, on every code of the stack; swipe or arrow between
  them.
- Show a pass on a white card, ready to scan; **tap the code to expand it
  full-screen** on a 1:1 square; the **X** deletes the code you're viewing
  (after asking first — a stacked pass keeps its other codes).
- **Details panel** per pass: issuer, date range (labeled **Dates** when both
  ends are set), time range, location, notes — shared by all the pass's stacked
  codes — plus a read-only **Code** row showing each code's decoded payload.
- **Edit** any pass: rename, fill or clear the details, delete a single code.
- The code-entry keyboard is calm by design: no mic or emoji keys, capitalized
  start, `SAVE` bottom-centre, larger input text.
- All stored codes render at full size — the renderer supports 13 barcode
  formats (QR Code, Aztec, EAN-13, EAN-8, PDF417, UPC-E, Data Matrix,
  Code 39, Code 93, ITF-14, Codabar, Code 128, UPC-A); Aztec/PDF417 keep their
  raw bytes so ticketing codes reproduce 1:1.

## Screenshots

| | |
|---|---|
| ![Passes list](screenshots/home.png) | ![Barcode stack](screenshots/barcode-stacked.png) |
| ![Details](screenshots/details.png) | ![Scanner](screenshots/scanner.png) |

## Build

Single-module Gradle project consuming the SDK as an included build:

```bash
source tools/env.sh
tools/build --dir passes :app:assembleDebug
```

APK (signed with the SDK dev keystore):

- Tool: `app/build/outputs/apk/debug/app-debug.apk` — `com.lightphone.passes`

## Install & run (emulator)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# the tool's camera permission is granted at install; on the emulator:
adb shell pm grant com.lightphone.passes android.permission.CAMERA
adb shell am start -n com.lightphone.passes/com.thelightphone.sdk.LightActivity
```

The tool also appears in the LightOS toolbox. `lighttool.toml`'s
`serverPackage` is required by the plugin; the merged build has no companion,
so it points at the **platform's own SDK server** — `com.lightos` on a real
LP3 (which hosts the service LightOS uses for runtime permission requests,
verified 2026-08-18). When testing scanning on the emulator, flip it to
`com.thelightphone.sdk.emulator` (or pre-grant CAMERA with `pm grant`).

## License

MIT — see [LICENSE](LICENSE). Original work © Vandam Dinh.
