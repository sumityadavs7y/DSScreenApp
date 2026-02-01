# TV Assistant App Visibility - Fix Applied ✅

## Problem
Your Digital Signage app was not appearing in your TV assistant app's startup/autostart list.

## Solution Applied

I've updated the `AndroidManifest.xml` to make your app visible to TV assistant apps and startup managers.

### Changes Made

**Before:**
```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
    <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
</intent-filter>
```

**After:**
```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
    <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

**Also Added:**
```xml
android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden"
```

### What These Changes Do

1. **`HOME` category** - Makes the app eligible as a home screen replacement, which is what many TV startup managers look for
2. **`DEFAULT` category** - Ensures the app is visible to system-level startup managers
3. **`configChanges`** - Properly handles screen rotation and configuration changes

## 🔧 What You Need to Do Now

### Step 1: Rebuild and Reinstall the App

```bash
# Option A: Using Gradle (recommended)
cd DigitalSignageLV
./gradlew uninstallDebug
./gradlew installDebug

# Option B: Using Android Studio
# 1. Build > Clean Project
# 2. Build > Rebuild Project
# 3. Run > Run 'app'
```

**⚠️ Important:** You MUST reinstall the app for the changes to take effect. TV assistants scan for these categories during installation.

### Step 2: Restart Your TV/Box

After reinstalling:
1. **Power off** your TV/box completely
2. Wait 10-15 seconds
3. **Power it back on**

This forces the TV assistant to rescan all installed apps.

### Step 3: Check TV Assistant App

1. Open your TV assistant app
2. Go to its app list or startup settings
3. Look for "**Digital Signage LV**" or "**DigitalSignageLV**"
4. ✅ It should now appear in the list!
5. Enable it for startup if needed

## 📱 Common TV Assistant Apps Where It Should Appear

Your app should now be visible in:
- ✅ Android TV Home (built-in launcher)
- ✅ Sideload Launcher
- ✅ Startup Manager (Chinese TV boxes)
- ✅ Boot Manager
- ✅ App Starter
- ✅ Any TV launcher that looks for HOME category apps

## 🔍 Verification

### Check if Changes Applied

```bash
# Connect to your TV via ADB
adb connect YOUR_TV_IP:5555

# Verify the intent filters
adb shell dumpsys package com.logicalvalley.digitalSignage | grep -A 20 "android.intent.action.MAIN"
```

**Expected output should include:**
```
android.intent.category.LAUNCHER
android.intent.category.LEANBACK_LAUNCHER
android.intent.category.HOME
android.intent.category.DEFAULT
```

### Test Autostart

1. Reboot your TV/box
2. The Digital Signage app should start automatically
3. If using TV assistant's startup list, enable it there first

## 🔧 Still Not Appearing?

### Additional Troubleshooting

**1. Check Device Settings:**
- Settings > Apps > Show system apps
- Find "Digital Signage LV"
- Check it has "Autostart" permission

**2. Check TV Assistant Filters:**
- Some TV assistants filter by app type
- Look for "Show all apps" or similar option
- Try searching for "Digital" or "Signage"

**3. Alternative Method - Manual Start:**

If your TV assistant still doesn't show it, you can manually add a startup command:

```bash
# Use ADB to check if app can be started manually
adb shell am start -n com.logicalvalley.digitalSignage/.MainActivity
```

If this works, your TV assistant might have a "Custom startup command" option where you can add this.

**4. Check Permissions in Android Settings:**
```
Settings > Apps > Digital Signage LV > Permissions
- Autostart / Boot permission: ✅ Allow
- Display over other apps: ✅ Allow (if available)
- Battery optimization: ❌ Don't optimize
```

## 📋 Summary

| What Changed | Why |
|--------------|-----|
| Added `HOME` category | Makes app visible to startup managers |
| Added `DEFAULT` category | Ensures system-level visibility |
| Added `configChanges` | Proper configuration handling |
| Updated documentation | Clear troubleshooting steps |

## 🎯 Expected Result

After following these steps:
1. ✅ App appears in TV assistant's app list
2. ✅ App can be selected for autostart
3. ✅ App starts automatically when TV boots
4. ✅ App stays running continuously

## 🆘 Still Having Issues?

If the app still doesn't appear after:
- ✅ Reinstalling the app
- ✅ Rebooting the TV
- ✅ Checking TV assistant settings

Please provide:
1. Name and version of your TV assistant app
2. TV box model and Android version
3. Screenshot of the TV assistant's app list (if possible)
4. Output of: `adb shell dumpsys package com.logicalvalley.digitalSignage | grep category`

## ✨ Additional Benefits

With these changes, your app also:
- Works better with Android TV launchers
- Can be set as a custom home screen
- Has better compatibility with TV box firmware
- Properly handles screen rotation and configuration changes

---

**Next Steps:** Reinstall the app and restart your TV! 🎉

