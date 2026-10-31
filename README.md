# BlueDeck

Change the app name in app/src/main/res/values/strings.xml. The icon is in app/src/main/res/drawable/ic_fg.xml.

Customizable Bluetooth remote (BLE + Classic SPP).

## Build on GitHub (no Android Studio needed)
1. Upload everything in this folder to a new GitHub repo (keep the `.github` folder).
2. Open the repo > **Actions** tab > "Build APK" runs automatically (or press **Run workflow**).
3. When it turns green (about 3-5 min), open the run and download **BtRemote-debug-apk** under Artifacts. Unzip it and install `app-debug.apk` on your phone (allow "install unknown apps").

## Using the app
- Connect: Classic SPP (pair HC-05/HC-06 in Android settings first) or BLE (HM-10 / ESP32, scans).
- Turn on Edit: drag a button to move it, drag the white corner dot to resize, tap it to edit. + Add makes a new one, Copy duplicates, Delete removes. Types: Hold, Toggle, Slider, Stick (joystick, use {x} and {y} e.g. J{x},{y}). Pick any colour.
- Digital/Hold: "Sends on press" (e.g. E1), "Sends on release" (e.g. E0 = stop), optional repeat while held.
- Slider: use {v} as the value, e.g. E1.{v} -> E1.37
- Raw bytes: start with hex: e.g. hex:E1 0A. Text gets the line ending chosen in Connect.
- Wrong wiring? Swap the values in Edit. No rebuild.

## Settings (top-right menu > Settings)
Line ending, prefix/suffix, SPP UUID, BLE service/write/notify UUIDs, write type, packet size and delay,
auto-reconnect, send-on-connect command, heartbeat, send rate, vibration, keep screen on, orientation,
edit grid snap, copy/paste layout. Menu > Monitor shows TX/RX data and lets you send test commands.
Per button: type, label, commands, colour, text size, corner radius, slider step, stick dead zone / invert.
