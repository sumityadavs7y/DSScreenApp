# Auto-Start and Keep Running Configuration

This document explains how the Digital Signage app is configured to start automatically on device boot and stay running continuously.

## ✅ Implemented Features

### 1. **Auto-Start on Boot**
- The app automatically starts when the device is powered on or rebooted
- Uses `BootReceiver` to listen for `BOOT_COMPLETED` broadcast

### 2. **Foreground Service**
- Runs a foreground service to prevent Android from killing the app
- Shows a persistent notification (required by Android for foreground services)
- Service restarts automatically if killed by the system

### 3. **Screen Always On**
- Screen stays on 24/7 (essential for digital signage)
- Uses `FLAG_KEEP_SCREEN_ON` window flag
- No screen timeout while app is running

### 4. **Battery Optimization Exemption**
- Requests exemption from battery optimization
- Prevents Android's Doze mode from killing the app
- App will prompt user to allow this on first run

### 5. **Immersive Full-Screen Mode**
- Hides status bar and navigation bar
- Provides edge-to-edge display for digital signage
- System UI reappears with swipe gesture if needed

### 6. **Device-Controlled Orientation**
- App respects device orientation settings
- No forced orientation lock

### 7. **TV Launcher Integration**
- Appears in Android TV launcher
- Visible in TV assistant apps and startup managers
- Compatible with TV box startup configuration tools

## 📺 TV Assistant App Visibility

### Making the App Appear in TV Startup Managers

The app is now configured with multiple intent categories to ensure it appears in TV assistant apps and startup managers:

```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
    <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

**What these do:**
- `LAUNCHER` - Makes app visible in standard Android launcher
- `LEANBACK_LAUNCHER` - Makes app visible in Android TV launcher
- `HOME` - Makes app eligible as a home screen replacement/startup app
- `DEFAULT` - Ensures visibility to system-level startup managers

### If Your App Still Doesn't Appear

**Step 1: Reinstall the App**
```bash
# Uninstall first
./gradlew uninstallDebug

# Then install
./gradlew installDebug
```
> TV assistants often scan for apps only during installation

**Step 2: Restart Your TV/Box Completely**
- Power off the device completely
- Wait 10 seconds
- Power it back on
- This forces the TV assistant to rescan all apps

**Step 3: Check TV Assistant Settings**
1. Open your TV assistant app (e.g., "Startup Manager", "TV Launcher", etc.)
2. Go to Settings or App Management
3. Look for "Digital Signage LV" or "DigitalSignageLV"
4. If found but disabled, enable it and set it to start on boot

**Step 4: Verify App Installation**
1. Go to Android Settings > Apps
2. Find "Digital Signage LV"
3. Ensure it has all required permissions:
   - Autostart / Boot completed
   - Display over other apps (if applicable)
   - Battery optimization disabled

**Step 5: Check Device Settings**
Some TV boxes have a built-in startup manager:
1. Settings > Device Preferences > Apps
2. Look for "Startup apps" or "Auto-start apps"
3. Enable "Digital Signage LV"

### Common TV Assistant Apps

The app should now appear in these common TV startup managers:
- **Android TV Home** - Built-in Android TV launcher
- **Sideload Launcher** - Popular for sideloaded apps
- **Startup Manager** - Available on many Chinese TV boxes
- **Boot Manager** - Common on Android TV boxes
- **App Starter** - Available on some TV boxes

### Alternative: Manual Autostart via ADB

If your TV assistant still doesn't recognize the app, you can use ADB to start it automatically:

**Create a startup script on your computer:**
```bash
#!/bin/bash
# File: start_signage.sh
adb connect YOUR_TV_IP:5555
adb shell am start -n com.logicalvalley.digitalSignage/.MainActivity
```

**Or set up a boot trigger on the TV itself** (requires root or custom ROM):
```bash
# On the TV/box (if you have shell access)
echo "am start -n com.logicalvalley.digitalSignage/.MainActivity" >> /data/local/bootscript.sh
```

### Verification

To verify the app is properly configured for TV assistants:

```bash
# Check if app is registered as a launcher
adb shell dumpsys package com.logicalvalley.digitalSignage | grep -A 20 "android.intent.action.MAIN"

# You should see:
# - android.intent.category.LAUNCHER
# - android.intent.category.LEANBACK_LAUNCHER
# - android.intent.category.HOME
# - android.intent.category.DEFAULT
```

## 📋 Required Permissions

The following permissions are included in `AndroidManifest.xml`:

```xml
<!-- Auto-start -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Keep running -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- Battery optimization -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- Notifications (required for foreground service) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

## 🔧 Device-Specific Setup

Some manufacturers have additional restrictions. Here's how to configure popular brands:

### Samsung Devices
1. Go to **Settings** → **Apps** → **Digital Signage**
2. Select **Battery** → Enable **"Allow background activity"**
3. Under **Battery usage**, set to **"Unrestricted"**
4. Go back to app info → Select **"Autostart"** or **"Run in background"** → Enable

### Xiaomi (MIUI) Devices
1. Go to **Settings** → **Apps** → **Manage apps** → **Digital Signage**
2. Enable **"Autostart"**
3. Select **Battery saver** → Choose **"No restrictions"**
4. Under **Other permissions** → Enable **"Display pop-up windows while running in background"**

### Huawei Devices
1. Go to **Settings** → **Apps** → **Apps** → **Digital Signage**
2. Enable **"Run in background"**
3. Go to **Battery** → **App launch** → Disable **"Manage automatically"**
4. Enable all three options: **Auto-launch**, **Secondary launch**, **Run in background**

### OnePlus / Oppo Devices
1. Go to **Settings** → **Apps** → **Digital Signage**
2. Select **Battery** → Enable **"Don't optimize"**
3. Enable **"Allow background activity"**
4. Go to **Advanced** → Enable **"Startup manager"**

### Stock Android / Google Pixel
1. Go to **Settings** → **Apps** → **Digital Signage**
2. Select **Battery** → Choose **"Unrestricted"**
3. Enable **"Remove permissions if app isn't used"** → Toggle OFF

## 🚀 Testing Auto-Start

### Test Boot Receiver
1. Reboot your device
2. Wait for device to fully boot
3. The app should launch automatically
4. Check logcat: `adb logcat | findstr "BootReceiver"`
   - You should see: `🚀 Device boot detected! Starting Digital Signage app...`

### Test Foreground Service
1. Launch the app
2. Press home button (app goes to background)
3. Pull down notification shade
4. You should see: "Digital Signage - Running in background"
5. Check logcat: `adb logcat | findstr "KeepAliveService"`

### Test Screen Always On
1. Launch the app
2. Leave it running
3. Screen should never turn off automatically
4. Device will not go to sleep

## 📊 Monitoring

### Check if App is Running
```bash
# Check foreground service
adb shell dumpsys activity services | findstr KeepAliveService

# Check if app process is alive
adb shell ps | findstr com.logicalvalley.digitalSignage
```

### View Logs
```bash
# Boot events
adb logcat | findstr "BootReceiver"

# Service events
adb logcat | findstr "KeepAliveService"

# Main activity events
adb logcat | findstr "MainActivity"
```

## 🔒 Security Considerations

### Why These Permissions Are Needed

- **RECEIVE_BOOT_COMPLETED**: Essential for unattended digital signage displays that need to work after power loss/reboot
- **FOREGROUND_SERVICE**: Prevents Android from killing the app to save battery
- **WAKE_LOCK**: Keeps screen on 24/7 for continuous display
- **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS**: Exempts app from Doze mode restrictions

### Privacy
- All permissions are used solely for the digital signage functionality
- No user data is collected or transmitted for these features
- The foreground notification is transparent about what the app is doing

## 🛠️ Troubleshooting

### App Doesn't Start on Boot
1. Check if RECEIVE_BOOT_COMPLETED permission is granted
2. Verify BootReceiver is enabled in manifest
3. Test manually: `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED`
4. Check device-specific auto-start settings (see above)

### App Gets Killed After Some Time
1. Disable battery optimization (Settings → Battery → Battery optimization → Digital Signage → Don't optimize)
2. Check manufacturer-specific battery/background restrictions
3. Verify foreground service is running: `adb shell dumpsys activity services`

### Screen Turns Off
1. Check if FLAG_KEEP_SCREEN_ON is set in MainActivity
2. Verify device's display timeout settings aren't overriding
3. Some devices need Developer Options → "Stay awake" enabled

### Foreground Notification Shows
- This is required by Android for foreground services
- Cannot be hidden on Android 8.0+ without root
- You can minimize it by setting notification importance to LOW

## 📱 Recommended Device Settings

For optimal digital signage operation:

1. **Display**
   - Brightness: 100% (or set adaptive brightness)
   - Screen timeout: Keep screen on (if available)
   - Auto-rotate: Configure based on your signage setup (ON/OFF)

2. **Power**
   - Battery saver: OFF
   - Adaptive battery: OFF
   - App standby: OFF for this app

3. **Developer Options** (if available)
   - Stay awake: ON (screen stays on while charging)
   - Don't keep activities: OFF
   - Background process limit: Standard limit

4. **Updates**
   - Disable automatic system updates during display hours
   - Or schedule updates for maintenance windows

## 🔄 Manual Controls

### Force Restart App
```bash
adb shell am force-stop com.logicalvalley.digitalSignage
adb shell am start -n com.logicalvalley.digitalSignage/.MainActivity
```

### Stop Foreground Service
```bash
adb shell am stopservice com.logicalvalley.digitalSignage/.service.KeepAliveService
```

### Start Foreground Service
```bash
adb shell am start-foreground-service com.logicalvalley.digitalSignage/.service.KeepAliveService
```

## 📝 Notes

- The app is designed for dedicated digital signage devices
- Not recommended for personal phones due to battery drain
- Screen always-on will significantly reduce battery life on devices not connected to power
- Keep devices plugged in for continuous operation

