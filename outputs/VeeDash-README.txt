VeeDash debug APK
=================

APK:
VeeDash-debug.apk

What it does:
- Connects to Bluetooth Classic ELM327 adapters and BLE adapters such as Veepeak OBDCheck BLE+.
- Reads RPM, speed, coolant temp, battery/control-module voltage, engine load, and throttle.
- Runs locally only. No account, login, analytics, cloud, or internet connection needed.
- Sends debug lines over your local Wi-Fi to this PC while we test.
- Shows a lower-right STATUS/CHAT box on the radio so I can send instructions back to the dash.
- Pulls PC GUI edits from VeeDash-config.json while the app is running.
- Saves the last working adapter and auto-reconnects after a car/radio/adapter reset.
- Lets you choose a background image from the radio's storage.
- Lets you move gauges around in edit mode. Double tap a gauge in edit mode to resize it.

How to use:
1. Plug in the Veepeak reader and turn the key to ACC or start the car.
2. Install VeeDash-debug.apk on the radio.
3. Open VeeDash and allow Bluetooth/location permissions if Android asks.
4. Tap Refresh to reload paired Classic devices.
5. Tap Scan to search both ways: Classic discovery and BLE scan.
6. Use the < and > buttons to cycle through devices. The selected device is shown beside Device.
7. VeeDash auto-selects likely Veepeak/OBD entries when it finds them.
8. The PIN button defaults to PIN 1234. Tap it to cycle PIN 0000 or PIN off.
9. If ClassicFound VEEPEAK appears, select it with < or > and tap Connect. VeeDash will try to start pairing from inside the app and auto-send the selected PIN when Android allows it.
10. If pairing stays stuck, tap PIN to switch between 1234 and 0000, then try Connect again.
11. Tap AutoTry for the aggressive Classic path. It tries common PINs and several RFCOMM socket/channel methods until the adapter answers ELM commands.
12. If Classic does not work, try BLE VEEPEAK.
13. Tap Background to choose an image.
14. Tap Edit, drag the gauges where you want them, double tap to resize, then tap Done.

Install from the radio:
http://192.168.0.130:8765/VeeDash-debug.apk

Diagnostics:
- The diagnostic panel is visible by default at the bottom of the screen.
- The diagnostic panel now sits near the top of the screen.
- Tap Log to show or hide the diagnostic panel.
- Long-press Log to copy the diagnostic text.
- If connection fails, take a photo of the Log panel or send the copied text.
- This build also posts fresh debug lines to:
  http://192.168.0.130:8766/log
- The PC receiver writes them here:
  VeeDash-live-log.txt
- The lower-right CHAT message comes from:
  VeeDash-message.txt
- Edit VeeDash-message.txt on this PC and the radio should update the chat box within a couple seconds.
- Run VeeDash-PC-Editor.py on this PC to edit the dash layout live. It writes:
  VeeDash-config.json
- The radio pulls that config from:
  http://192.168.0.130:8766/config
- The PC editor can move/resize gauges, hide gauges, change colors, show/hide chat/log, and toggle auto-reconnect.
- The upgraded PC editor also lets you play with motion GIF/image backgrounds, drag the preview directly, layer gauges/log/chat, and choose number/graph/both/ring/bar modes. Those new visual modes are preview/config first; the APK currently applies positions, sizes, visibility, colors, log/chat, and reconnect.
- If Windows Firewall asks about Python, allow it on Private networks.

More OBD data we can add:
- Fuel trim short/long term, intake air temp, MAF, timing advance, fuel level, run time, distance since codes cleared, commanded equivalence ratio, O2 sensors, catalyst temp, barometric pressure, ambient air temp, oil temp if supported, transmission temp if the car exposes it, VIN, trouble codes, and readiness monitors.
- Display styles can be number only, graph only, number plus graph, ring gauge, horizontal/vertical bar, min/max trail, warning color bands, or a simple shift-light style strip.
- The top of the panel has screenshot-friendly fields: phase, selected device, scan result, service count, write/notify UUIDs, last TX/RX, and last error.
- The raw log underneath shows newest entries first so a screenshot captures the latest failure.
- If modern BLE scan fails with code 3, VeeDash automatically retries with Android's older legacy BLE scanner.
- On Android 6-11, Location must often be turned on for BLE scan even when the app already has location permission.

Notes:
- Your pictured adapter appears to be Veepeak OBDCheck BLE+, so use Scan inside VeeDash instead of trying to pair it from Android's Bluetooth screen.
- The APK is debug-signed, so Android may warn that it is from an unknown source. That is normal for a local test build.
- If your car is off or the adapter is asleep, the dashboard may connect but show blank values until the ECU responds.
- When the car goes from ACC/on to RUN, the Veepeak may reset. VeeDash waits a few seconds and reconnects to the saved adapter automatically unless you intentionally tapped Connect/Auto to stop it.
