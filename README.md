# BlueDeck

[![Build APK](https://github.com/KinglessCrown/BlueDeck-Custom-Bluetooth-Remote/actions/workflows/build.yml/badge.svg)](https://github.com/KinglessCrown/BlueDeck-Custom-Bluetooth-Remote/actions/workflows/build.yml)
[![Download](https://img.shields.io/github/v/release/KinglessCrown/BlueDeck-Custom-Bluetooth-Remote?label=download&color=blue)](https://github.com/KinglessCrown/BlueDeck-Custom-Bluetooth-Remote/releases/latest)
![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84)
![License](https://img.shields.io/badge/license-MIT-blue)

**A fully customizable Bluetooth remote control for Android.**
Connect over **BLE** or **Classic Bluetooth serial (SPP)**, then design your own control panel: place buttons, sliders and joysticks anywhere, and decide exactly what data each one sends.

Changed your protocol? Wired a motor backwards? Just edit the value in the app. No rebuild needed.

## Features

- **BLE and Classic SPP** (HC-05, HC-06, HM-10, ESP32 and similar modules)
- **Free-form layout:** drag to move, drag the corner dot to resize, add, copy and delete controls
- **Control types:** Hold button, Toggle (ON/OFF values), Slider (analog), Joystick (analog stick)
- **Custom data per control:** any text or raw hex bytes, with placeholders for analog values
- **Per-control style:** colour, text size, corner radius
- **Joystick options:** dead zone, invert X/Y, angle and strength output
- **Communication settings:** line ending, prefix/suffix, SPP UUID, BLE service/write/notify UUIDs, write type, packet size and delay
- **Connection tools:** auto-reconnect, send-on-connect command, heartbeat
- **Monitor:** live TX/RX log (text or hex) with a manual send box
- **Backup:** copy and paste your whole layout through the clipboard

## Quick start

1. Install the APK (see [Download](#download)).
2. Pair your module in Android Bluetooth settings (needed for Classic SPP only).
3. Tap **Connect**, choose **Classic SPP** or **BLE**, and pick your device.
4. Tap **Edit** to arrange and change controls. Tap **Done** when finished.

## Sending data

Each control has a **Sends** value. Text is sent with the prefix, suffix and line ending set in **Settings**.

| You type | What is sent |
|---|---|
| `E1` | `E1` + line ending |
| `E1.{v}` (slider at 37) | `E1.37` + line ending |
| `J{x},{y}` (stick) | `J50,-30` + line ending |
| `hex:E1 0A` | raw bytes `E1 0A`, nothing added |

| Control | Placeholders |
|---|---|
| Slider | `{v}` value from Min to Max |
| Stick | `{x}` `{y}` from Min to Max, `{a}` angle 0-359 (0 = up, clockwise), `{m}` strength 0-100 |

Escapes `\n`, `\r`, `\t` and `\0` work inside text commands.

| Type | Behaviour |
|---|---|
| Hold | Sends on press, optional command on release, optional repeat while held |
| Toggle | Sends the ON value, then the OFF value on the next press |
| Slider | Sends while you drag, optional command on release |
| Stick | Sends while you drag, centre values and optional command on release |

## Download

Builds are made automatically with GitHub Actions.

1. Open the **Actions** tab and select the latest green **Build APK** run.
2. Download **BlueDeck-debug-apk** under *Artifacts* and unzip it.
3. Install `app-debug.apk` on your phone (allow "install unknown apps").

## Build from source

Requires JDK 17 and the Android SDK (or just open the project in Android Studio).

```
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

## Project structure

```
app/src/main/java/com/example/btremote/
  MainActivity.kt   UI: control canvas, editor, buttons, slider, stick
  Bt.kt             Bluetooth layer: BLE + Classic SPP, send queue, reconnect
  Store.kt          Saved layout and settings
  Dialogs.kt        Connect, Settings and Monitor dialogs
.github/workflows/  Automatic APK build
```

## Permissions

| Permission | Why |
|---|---|
| Bluetooth connect / scan (Android 12+) | Connect to and find your device |
| Location (Android 11 and older) | Required by Android to scan for BLE |

No internet access, no accounts, no tracking. All settings stay on your phone.

## Safety

BlueDeck sends exactly what you configure. When controlling motors or other hardware, test with the wheels off the ground, and add a failsafe (such as stopping on lost connection) in your own firmware.

## Contributing

Issues and pull requests are welcome. Please describe your phone model, Android version and Bluetooth module when reporting a connection problem, and include the Monitor log if you can.

## License

Released under the [MIT License](LICENSE).
