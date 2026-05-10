# BikeNav Android App

Automatically reads Google Maps navigation notifications and sends them wirelessly to your ESP32 OLED display via Bluetooth.

## How It Works

```
Google Maps Navigation
        ↓
Android Notification
        ↓
NavNotificationListenerService (reads it)
        ↓
Parses: direction + distance + ETA
        ↓
Builds: "LEFT|200m|8:42PM"
        ↓
BluetoothService (sends it)
        ↓
ESP32 BikeNav device
        ↓
OLED Display updates ✅
```

---

## Project Structure

```
BikeNav/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/bikenav/app/
│   │   ├── MainActivity.java                  ← UI, device picker, live log
│   │   ├── NavNotificationListenerService.java ← reads Google Maps notifications
│   │   ├── BluetoothService.java              ← Bluetooth connection + sender
│   │   └── BootReceiver.java                  ← auto-start on phone reboot
│   └── res/
│       ├── layout/activity_main.xml
│       └── values/...
└── README.md
```

---

## Setup Instructions

### Step 1: Open in Android Studio
1. Open Android Studio
2. File → Open → select the `BikeNav` folder
3. Wait for Gradle sync to complete

### Step 2: Pair Your ESP32
1. On your phone: Settings → Bluetooth → Scan
2. Pair with `BikeNav` (your ESP32)
3. Make sure it appears in your paired devices list

### Step 3: Build & Install
1. Connect your Android phone via USB
2. Enable USB Debugging (Developer Options)
3. Click ▶ Run in Android Studio
4. App installs on your phone

### Step 4: Grant Notification Access
1. Open BikeNav app
2. Tap **"Grant Notification Access"**
3. Find **BikeNav** in the list and enable it
4. ⚠️ This is required — without it, the app cannot read Google Maps

### Step 5: Connect to ESP32
1. Tap **Connect** in the BikeNav app
2. If ESP32 is in paired list as "BikeNav" → auto-connects
3. Otherwise → pick it from the list

### Step 6: Start Navigation
1. Open Google Maps
2. Search for a destination
3. Tap **Start**
4. Watch your OLED display update automatically! 🎉

---

## Supported Directions

| Google Maps Says               | ESP32 Receives  |
|-------------------------------|-----------------|
| Turn left                     | LEFT            |
| Turn right                    | RIGHT           |
| Continue straight / Head north | STRAIGHT       |
| Keep left                     | KEEP_LEFT       |
| Keep right                    | KEEP_RIGHT      |
| Slight left                   | SLIGHT_LEFT     |
| Slight right                  | SLIGHT_RIGHT    |
| Sharp left                    | SHARP_LEFT      |
| Sharp right                   | SHARP_RIGHT     |
| Make a U-turn                 | UTURN           |
| At the roundabout...          | ROUNDABOUT      |
| Take the exit                 | EXIT            |
| You have arrived              | ARRIVED         |

---

## Protocol Format

```
DIRECTION|DISTANCE|ETA
```

Examples:
```
LEFT|200m|8:42PM
RIGHT|1.2km|9:10PM
STRAIGHT|---|9:05PM
ARRIVED|0m|DONE
```

---

## ESP32 Firmware (existing)

Your ESP32 firmware already works. The app sends the same protocol string you designed — no changes needed on the ESP32 side.

Just make sure your ESP32 reads serial until `\n`:

```cpp
if (SerialBT.available()) {
    String data = SerialBT.readStringUntil('\n');
    data.trim();
    parseNavData(data);
}
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| No directions on display | Check Notification Access is granted |
| Can't connect | Make sure ESP32 is paired and powered on |
| Wrong direction shown | Check Google Maps notification is visible in notification shade |
| App crashes | Check Bluetooth is enabled on phone |

---

## Permissions Required

| Permission | Why |
|-----------|-----|
| BLUETOOTH_CONNECT | Connect to ESP32 |
| BIND_NOTIFICATION_LISTENER_SERVICE | Read Google Maps notifications |
| FOREGROUND_SERVICE | Keep Bluetooth connection alive |
| RECEIVE_BOOT_COMPLETED | Auto-start after reboot |

---

## Future Improvements

- Speed display from GPS
- Battery level monitoring
- Auto-reconnect on signal loss (already implemented ✅)
- Dark/bright mode switching for day/night riding
