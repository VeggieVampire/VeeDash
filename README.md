# VeeDash

VeeDash is a lightweight Android dashboard for car radios/head units. It connects to a Veepeak/ELM327-style OBD adapter over Bluetooth Classic or BLE, reads local car data, and displays it with configurable gauges, images, GIF assets, debug logs, and a small chat/status popup from the PC editor.

This project was built for an older Android car radio, so it avoids accounts, cloud logins, analytics, and heavy online dependencies.

## What's Included

- `work/VeeDash/` - Android Studio / Gradle project for the APK.
- `outputs/VeeDash.apk` - current installable debug-signed APK.
- `outputs/VeeDash-PC-Editor.py` - PC GUI editor and local config server.
- `outputs/VeeDash-log-server.py` - lightweight local server for APK/config/log testing.
- `outputs/VeeDash-config.json` - staged dashboard layout/config file.
- `outputs/VeeDash-assets/` - staged images/GIFs used by the dashboard.

## Current Features

- Bluetooth Classic and BLE adapter discovery.
- Saved adapter auto-reconnect on startup and after car/radio power changes.
- OBD values for RPM, speed, coolant temperature, voltage, engine load, and throttle.
- Live `DATA ...` lines in the dash debug log and Wi-Fi log feed.
- Drag/resize gauge layout on the Android dash.
- PC editor for staging layouts, graph/number/ring/bar modes, colors, transparency, and GIF/image assets.
- Pull/push style local config sync over Wi-Fi.
- Popup chat/status messages from the PC to the car dash.
- Debug panel with version, selected adapter, connection phase, last TX/RX, and latest errors.

## Build The APK

From `work/VeeDash`:

```powershell
$env:JAVA_HOME='Z:\Android\Android Studio\jbr'
$env:ANDROID_HOME='Z:\Android\Sdk'
$env:ANDROID_SDK_ROOT='Z:\Android\Sdk'
.\gradlew.bat assembleDebug
```

The APK will be generated at:

```text
work/VeeDash/app/build/outputs/apk/debug/app-debug.apk
```

## Run The PC Editor

From the repository root:

```powershell
python outputs\VeeDash-PC-Editor.py
```

The editor also runs the local config/log server, normally on port `8766`.

## Install

Copy or serve `outputs/VeeDash.apk` to the Android radio, install it, then use the in-app menu to scan/select the Veepeak adapter.

The APK is debug-signed, so Android may warn about installing from an unknown source.
