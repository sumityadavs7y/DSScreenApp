# Screen Rotation Control Feature

This document explains how to control screen rotation in the Digital Signage app.

## 🔄 Overview

The app includes a simple screen rotation control with just 3 options:
- **Clockwise** - Rotate screen 90° clockwise
- **Anti-Clockwise** - Rotate screen 90° counter-clockwise  
- **Auto** - Follow device rotation setting

Much simpler than choosing specific angles! Just click clockwise or anti-clockwise until the screen looks right.

## 🎯 Rotation Controls

### 1. **↻ Clockwise Button**
- Rotates screen 90° clockwise
- Each click rotates further: 0° → 90° → 180° → 270° → 0°
- Keeps rotating with each click
- Perfect for: Finding the right orientation step by step

### 2. **↺ Anti-Clockwise Button**
- Rotates screen 90° counter-clockwise
- Each click rotates further: 0° → 270° → 180° → 90° → 0°
- Opposite direction of clockwise
- Perfect for: Going back if you rotated too far

### 3. **Auto (Device Setting)**
- Follows device's auto-rotate setting
- Screen rotates based on how device is physically held
- Green button when active (✓ Auto)
- Perfect for: Flexible installations where orientation changes

## 🎮 How to Rotate the Screen

### Step-by-Step:

1. **Access Stats Screen:**
   - While playing content, press the **Back** button
   - Device Statistics screen appears

2. **Find Rotation Controls:**
   - Look for "Screen Rotation" section (left column)
   - Shows current orientation (e.g., "Portrait (0°)", "Landscape (90°)")

3. **Rotate the Screen:**
   
   **Option A - Clockwise:**
   - Click **"↻ Clockwise"** button
   - Screen rotates 90° clockwise instantly
   - Keep clicking to rotate further if needed
   - Each click = another 90° clockwise
   
   **Option B - Anti-Clockwise:**
   - Click **"↺ Anti-Clockwise"** button  
   - Screen rotates 90° counter-clockwise instantly
   - Keep clicking to rotate further if needed
   - Each click = another 90° counter-clockwise
   
   **Option C - Auto Mode:**
   - Click **"Auto (Device Setting)"** button
   - Screen follows device's auto-rotate setting
   - Button turns green when active (✓ Auto)

4. **Done!**
   - Rotation applies immediately
   - Setting saves automatically
   - Persists after app restart and reboot

5. **Return to Playback:**
   - Click **"Back to Playlist"**
   - Content continues in new orientation

## 💾 Persistence

- **Saved Automatically:** Rotation preference is saved to device storage
- **Survives Restart:** Setting persists when app is closed and reopened
- **Survives Reboot:** Setting persists when device is rebooted
- **Per-Device:** Each device can have its own rotation setting

## 🖼️ Rotation Controls UI

```
Screen Rotation
Current: Landscape (90°)

[↻ Clockwise]  [↺ Anti-Clockwise]

[✓ Auto (Device Setting)]  ← Green when active
```

- **Blue Buttons:** Clockwise and Anti-Clockwise rotation
- **Green Button:** Auto mode (when active)
- **Gray Button:** Auto mode (when locked angle is set)
- **Current Status:** Shows current angle (0°, 90°, 180°, 270°) or "Auto"

## 📱 Use Cases

### Digital Signage Installations

**Wall-Mounted Horizontal Display (Most Common):**
```
1. Click "Clockwise" until screen is horizontal
2. Usually takes 1 click from portrait
3. Final rotation: Landscape (90°)
```

**Wall-Mounted Vertical Display:**
```
1. Use default orientation or click until vertical
2. Final rotation: Portrait (0°)
```

**Ceiling-Mounted or Upside-Down Display:**
```
1. Click "Clockwise" or "Anti-Clockwise" until correct
2. Usually 2 clicks from default orientation
3. Final rotation: 180° or 270° depending on mount
```

**Rotating Kiosk:**
```
1. Click "Auto (Device Setting)" button
2. Enable auto-rotate in device settings
3. Screen follows physical orientation
```

**Quick Tip:**
- If you rotate too far, use "Anti-Clockwise" to go back
- Each button click = 90° rotation
- It cycles through: 0° → 90° → 180° → 270° → 0°

## 🔧 Technical Details

### How It Works

1. **User Selection:** User clicks a rotation button in Stats Screen
2. **MainActivity Receives:** Callback passes rotation value to MainActivity
3. **Apply Orientation:** MainActivity sets `requestedOrientation` property
4. **Save Preference:** Rotation value saved to DataStore
5. **Persist:** On next app launch, saved rotation is loaded and applied

### Rotation Values

| Button | Value | Android Constant |
|--------|-------|------------------|
| Auto | `"AUTO"` | `SCREEN_ORIENTATION_UNSPECIFIED` |
| 0° | `"0"` | `SCREEN_ORIENTATION_PORTRAIT` |
| 90° | `"90"` | `SCREEN_ORIENTATION_LANDSCAPE` |
| 180° | `"180"` | `SCREEN_ORIENTATION_REVERSE_PORTRAIT` |
| 270° | `"270"` | `SCREEN_ORIENTATION_REVERSE_LANDSCAPE` |

### Code Flow

**Clockwise Rotation:**
```
User clicks "↻ Clockwise"
    ↓
onRotateClockwise callback
    ↓
MainActivity.rotateClockwise()
    ↓
Current angle + 90° (mod 360)
    ↓
setScreenRotation() - applies immediately
    ↓
DataStoreManager.saveScreenRotation() - saves to storage
```

**Anti-Clockwise Rotation:**
```
User clicks "↺ Anti-Clockwise"
    ↓
onRotateAntiClockwise callback
    ↓
MainActivity.rotateAntiClockwise()
    ↓
Current angle - 90° (mod 360)
    ↓
setScreenRotation() - applies immediately
    ↓
DataStoreManager.saveScreenRotation() - saves to storage
```

**Auto Mode:**
```
User clicks "Auto (Device Setting)"
    ↓
onSetAutoRotation callback
    ↓
MainActivity.setAutoRotation()
    ↓
setScreenRotation("AUTO")
    ↓
requestedOrientation = UNSPECIFIED
    ↓
Saves to storage
```

## 🐛 Troubleshooting

### Rotation Not Changing
**Problem:** Clicked rotation button but screen didn't rotate

**Solutions:**
1. Check if device has "Auto-rotate" system setting that might interfere
2. Try "Auto" mode first, then switch back to desired angle
3. Restart the app
4. Check logcat: `adb logcat | findstr "Screen rotation"`

### Rotation Resets After Reboot
**Problem:** Saved rotation doesn't persist after device reboot

**Solutions:**
1. Ensure app has permission to write to storage
2. Check DataStore is working: `adb shell cat /data/data/com.logicalvalley.digitalSignage/files/datastore/settings.preferences_pb`
3. Verify auto-start is working (app must launch to apply rotation)

### Wrong Orientation at Startup
**Problem:** Screen shows wrong orientation when app first launches

**Solutions:**
1. Wait 1-2 seconds - rotation applies after app initialization
2. Check if correct rotation is saved in Settings
3. Try manually setting rotation again from Stats Screen

### Can't Access Stats Screen
**Problem:** Can't find Stats Screen to change rotation

**Solution:**
- Press the **Back button** while content is playing
- On TV remotes, this is usually the "Back" or "←" button
- On tablets, use the software back button or gesture

## 📊 Monitoring

### View Rotation Logs

```bash
# See rotation changes
adb logcat | findstr "Screen rotation"
```

Expected output:
```
MainActivity: 🔄 Loading saved rotation: 90
MainActivity: 🔄 Screen rotation set to: 90
```

### Check Current Rotation

```bash
# View current device orientation
adb shell dumpsys window | findstr "mCurrentRotation"
```

### Check Saved Rotation

```bash
# View saved preference (requires root or debuggable app)
adb shell run-as com.logicalvalley.digitalSignage cat files/datastore/settings.preferences_pb
```

## 💡 Best Practices

### For Installers

1. **Test First:** Test all 4 rotation angles before permanent installation
2. **Lock Rotation:** Once you find the correct angle, lock it (don't use Auto)
3. **Document:** Record which rotation setting was used for each display
4. **Verify:** After installation, reboot device to ensure rotation persists

### For Administrators

1. **Standardize:** Use the same rotation for all displays of the same type
2. **Remote Access:** Document how to access ADB for remote rotation changes
3. **Training:** Train staff on how to access Stats Screen and change rotation
4. **Backup:** Keep a record of rotation settings for each device ID

## 🔒 Security Note

- Rotation settings are stored locally on each device
- No network requests are made when changing rotation
- Settings are not synchronized across devices
- Each device maintains its own rotation preference independently

## 🆕 Future Enhancements

Potential future additions:
- [ ] Remote rotation control via admin dashboard
- [ ] Schedule-based rotation (e.g., portrait during day, landscape at night)
- [ ] Auto-detect optimal rotation based on content aspect ratio
- [ ] QR code configuration for bulk setup
- [ ] Rotation lock with password protection

## 📞 Support

If you encounter issues with screen rotation:

1. Check this documentation first
2. Review logcat output for error messages
3. Test with "Auto" mode to verify device rotation works
4. Ensure app has all required permissions
5. Contact support with device model and rotation logs

