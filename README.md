# Passes

An app for the Light Phone III to store and show codes — boarding passes,
library cards, memberships, anything a QR code represents.

Built as a **real LightOS tool** with the [light-phone/light-sdk](https://github.com/lightphone/light-sdk)
tool plugin: launched from the LightOS toolbox, UI on the Light design system,
code rendering in a companion server. Modeled on the SDK's Authenticator
example.

Forked from [vandamd/passes](https://github.com/vandamd/passes) (MIT).

## Features

- Store a code by **scanning a QR code** with the camera, or by **typing** it
  on the LP3 keyboard (typed codes are stored as QR).
- Show a pass on a white card, ready to scan; **tap the code to expand it
  full-screen** (back button only).
- Rename (tap the pass name), **remove** from the pass screen.
- All stored codes render at full size — the renderer supports 13 barcode
  formats (QR Code, Aztec, EAN-13, EAN-8, PDF417, UPC-E, Data Matrix,
  Code 39, Code 93, ITF-14, Codabar, Code 128, UPC-A).

## Build

Two-module Gradle project consuming the SDK as an included build:

```bash
source tools/env.sh
tools/build --dir passes :app:assembleDebug :server:assembleDebug
```

APKs (signed with the SDK dev keystore):

- Tool: `app/build/outputs/apk/debug/app-debug.apk` — `com.lightphone.passes`
- Companion: `server/build/outputs/apk/debug/server-debug.apk` — `com.lightphone.passes.server`

## Install & run (emulator)

```bash
adb install -r server/build/outputs/apk/debug/server-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
# the tool's camera permission is granted at install; on the emulator:
adb shell pm grant com.lightphone.passes android.permission.CAMERA
adb shell am start -n com.lightphone.passes/com.thelightphone.sdk.LightActivity
```

The tool also appears in the LightOS toolbox. On a real Light Phone III point
`lighttool.toml`'s `serverPackage` at `com.lightos` once Light ships these
methods in the production server (currently `com.lightphone.passes.server`,
which works on the emulator and any device with the companion installed).

## License

MIT — see [LICENSE](LICENSE). Original work © Vandam Dinh.
