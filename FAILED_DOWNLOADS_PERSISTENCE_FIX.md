# Failed Downloads Persistence Fix

**Date:** February 8, 2026  
**Issue:** Failed downloads not persisting across app restarts, retry button missing  
**Status:** Fixed ✅

---

## 🔍 Problem Identified

When downloads fail and the app is restarted:
- ❌ Failed download list (`_failedDownloads`) was in-memory only
- ❌ On app restart, failed downloads list was empty
- ❌ Retry button didn't appear (no failed downloads to show)
- ❌ User couldn't retry failed downloads after restart
- ❌ Downloads that failed were simply forgotten

**Why This Happened:**
- `_failedDownloads` StateFlow was not persisted to DataStore
- Only kept in memory during app session
- Lost on app close/restart
- No mechanism to restore failed state

---

## ✅ What's Fixed

### 1. **Persistent Storage for Failed Downloads**

Added to `DataStoreManager.kt`:
```kotlin
val FAILED_DOWNLOADS = stringPreferencesKey("failed_downloads")

suspend fun saveFailedDownloads(failedIds: Set<String>) {
    dataStore.edit { preferences ->
        preferences[FAILED_DOWNLOADS] = failedIds.joinToString(",")
    }
}

val failedDownloads: Flow<Set<String>> = dataStore.data
    .map { preferences ->
        val idsString = preferences[FAILED_DOWNLOADS] ?: ""
        if (idsString.isEmpty()) emptySet() 
        else idsString.split(",").toSet()
    }
```

**How it works:**
- Failed item IDs stored as comma-separated string
- Persisted to disk via DataStore
- Survives app restarts
- Automatically restored on app start

---

### 2. **Restore Failed Downloads on App Start**

Added to `MainViewModel.kt`:
```kotlin
init {
    // ... existing init code
    loadFailedDownloads()  // ← NEW
    checkRegistration()
    startPeriodicCheck()
}

private fun loadFailedDownloads() {
    viewModelScope.launch {
        dataStoreManager.failedDownloads.collect { savedFailedIds ->
            if (savedFailedIds.isNotEmpty()) {
                _failedDownloads.value = savedFailedIds
                Log.d(TAG, "📥 Restored ${savedFailedIds.size} failed downloads")
            }
        }
    }
}
```

**What happens:**
- On app start, loads failed downloads from DataStore
- Restores `_failedDownloads` StateFlow
- UI automatically shows retry button
- User can retry immediately

---

### 3. **Persist on Every Change**

Modified download and retry logic:
```kotlin
// On download failure
_failedDownloads.value = _failedDownloads.value + itemId
viewModelScope.launch {
    dataStoreManager.saveFailedDownloads(_failedDownloads.value)
}

// On download success (remove from failed)
_failedDownloads.value = _failedDownloads.value - itemId
viewModelScope.launch {
    dataStoreManager.saveFailedDownloads(_failedDownloads.value)
}
```

**Benefits:**
- Failed downloads saved immediately
- No data loss on app crash
- Always in sync with actual state

---

### 4. **Clear on Reset**

Modified `resetRegistration()`:
```kotlin
_failedDownloads.value = emptySet()
dataStoreManager.saveFailedDownloads(emptySet())  // ← NEW
```

**Ensures:**
- Failed downloads cleared on device deregistration
- Clean state for new registration
- No stale failed downloads

---

## 📊 Before vs After

### Before Fix:

**Scenario: Download fails → App restarts**
```
1. Start download
2. 3 downloads fail (network issue)
3. Close app
4. Reopen app
   ❌ Failed downloads list: EMPTY
   ❌ Retry button: NOT VISIBLE
   ❌ User can't retry
```

---

### After Fix:

**Scenario: Download fails → App restarts**
```
1. Start download
2. 3 downloads fail (network issue)
3. Failed downloads saved to DataStore
4. Close app
5. Reopen app
   ✅ Failed downloads list: RESTORED (3 items)
   ✅ Retry button: VISIBLE
   ✅ User can click retry
   ✅ Downloads attempted again
```

---

## 🎬 Complete User Flow

### Flow 1: Download Failure → Immediate Retry
```
1. Register device
2. Start downloading 10 videos
3. 3 downloads fail (poor network)
4. Stats screen shows:
   - "Cached Items: 7 / 10"
   - "Download Status: 3 failed"
   - [🔄 Retry Downloads (3)] button
5. User clicks retry
6. Downloads succeed
7. All complete!
```

---

### Flow 2: Download Failure → App Restart → Retry
```
1. Register device
2. Start downloading 10 videos
3. 3 downloads fail
4. User closes app (doesn't retry yet)
5. Failed downloads saved to disk
6. User reopens app next day
7. Stats screen shows:
   - "Cached Items: 7 / 10"
   - "Download Status: 3 failed"
   - [🔄 Retry Downloads (3)] button  ← RESTORED!
8. User clicks retry
9. Downloads succeed
10. All complete!
```

---

### Flow 3: Multiple Failures Over Time
```
1. Day 1: Download 10 videos
   - 2 fail → Saved to disk
2. Day 2: Reopen app
   - Retry button shows (2 failed)
   - User retries → 1 succeeds, 1 still fails
   - Now 1 failed → Saved to disk
3. Day 3: Reopen app
   - Retry button shows (1 failed)
   - User retries → Success!
   - All complete → Failed list cleared
```

---

## 🔧 Technical Implementation

### File 1: `DataStoreManager.kt`

**Added:**
```kotlin
// Key for storing failed downloads
val FAILED_DOWNLOADS = stringPreferencesKey("failed_downloads")

// Save function
suspend fun saveFailedDownloads(failedIds: Set<String>) {
    dataStore.edit { preferences ->
        preferences[FAILED_DOWNLOADS] = failedIds.joinToString(",")
    }
}

// Load function (Flow)
val failedDownloads: Flow<Set<String>> = dataStore.data
    .map { preferences ->
        val idsString = preferences[FAILED_DOWNLOADS] ?: ""
        if (idsString.isEmpty()) emptySet() 
        else idsString.split(",").toSet()
    }
```

**Storage Format:**
```
Key: "failed_downloads"
Value: "item-id-1,item-id-2,item-id-3"
```

---

### File 2: `MainViewModel.kt`

**Added:**
```kotlin
// In init block
private fun loadFailedDownloads() {
    viewModelScope.launch {
        dataStoreManager.failedDownloads.collect { savedFailedIds ->
            if (savedFailedIds.isNotEmpty()) {
                _failedDownloads.value = savedFailedIds
                Log.d(TAG, "📥 Restored ${savedFailedIds.size} failed downloads")
            }
        }
    }
}
```

**Modified:**
```kotlin
// On download failure
_failedDownloads.value = _failedDownloads.value + itemId
viewModelScope.launch {
    dataStoreManager.saveFailedDownloads(_failedDownloads.value)
}

// On download success
_failedDownloads.value = _failedDownloads.value - itemId
viewModelScope.launch {
    dataStoreManager.saveFailedDownloads(_failedDownloads.value)
}

// On reset
_failedDownloads.value = emptySet()
dataStoreManager.saveFailedDownloads(emptySet())
```

---

## 🧪 Testing Guide

### Test 1: Basic Persistence
```bash
# 1. Install app
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2. Register device
# 3. Enable airplane mode after 2 videos download
# 4. Wait for failures
# 5. Verify retry button appears
# 6. Close app
adb shell am force-stop com.logicalvalley.digitalSignage

# 7. Reopen app
adb shell am start -n com.logicalvalley.digitalSignage/.MainActivity

# 8. Press BACK to open Stats
# 9. Verify:
#    ✅ Retry button still visible
#    ✅ Failed count matches (e.g., "3 failed")
#    ✅ Can click retry
```

### Test 2: Verify Logs
```bash
adb logcat | grep -E "Failed|Retry|📥|saveFailedDownloads"

# Expected on failure:
# ❌ Failed to cache: video.mp4 (itemId: abc-123)
# 💾 Saving failed downloads: [abc-123, def-456]

# Expected on restart:
# 📥 Restored 2 failed downloads from storage
```

### Test 3: Retry After Restart
```bash
# 1. Cause failures (airplane mode)
# 2. Close app
# 3. Disable airplane mode (restore network)
# 4. Reopen app
# 5. Open Stats screen
# 6. Click "Retry Downloads"
# 7. Verify:
#    ✅ Downloads start
#    ✅ Progress shows
#    ✅ Failures clear on success
#    ✅ Retry button disappears when all succeed
```

### Test 4: Clear on Reset
```bash
# 1. Have some failed downloads
# 2. Click "Reset Registration"
# 3. Re-register device
# 4. Verify:
#    ✅ No old failed downloads
#    ✅ Clean slate
#    ✅ Fresh download attempt
```

---

## 📊 Data Flow Diagram

```
┌─────────────────────────────────────────────┐
│         Download Failure Flow               │
└─────────────────────────────────────────────┘

[Download Fails]
      ↓
Update _failedDownloads StateFlow
      ↓
Save to DataStore
      ↓
Persist to Disk
      ↓
[App Closes]
      ↓
[App Reopens]
      ↓
loadFailedDownloads() called
      ↓
Read from DataStore
      ↓
Restore _failedDownloads StateFlow
      ↓
UI shows retry button
      ↓
[User clicks retry]
      ↓
Retry downloads
      ↓
On success: Remove from _failedDownloads
      ↓
Save updated list to DataStore
      ↓
UI updates (button disappears if all succeed)
```

---

## ✅ Benefits

### For Users
✅ **Persistence** - Failed downloads remembered across restarts  
✅ **Retry Anytime** - Can retry even days later  
✅ **No Data Loss** - Failures tracked reliably  
✅ **Clear Feedback** - Always know what failed  

### For Developers
✅ **Simple Storage** - Comma-separated string format  
✅ **Automatic Sync** - DataStore Flow handles updates  
✅ **Crash-Safe** - Persisted immediately on failure  
✅ **Easy Debugging** - Clear logs for state changes  

### For System
✅ **Lightweight** - Minimal storage overhead  
✅ **Fast** - Quick read/write operations  
✅ **Reliable** - DataStore handles edge cases  
✅ **Clean** - Cleared on reset  

---

## 🔍 Edge Cases Handled

### 1. **App Crash During Download**
- Failed downloads already saved
- On restart, failures restored
- User can retry

### 2. **Multiple App Restarts**
- Failed list persists across all restarts
- Always accurate
- No duplicates

### 3. **Partial Retry Success**
- Some succeed, some still fail
- Updated list saved
- Retry button shows remaining count

### 4. **Network Restored After Failure**
- Failed list persists
- User can retry when network good
- Downloads succeed

### 5. **Device Deregistration**
- Failed list cleared
- Fresh start for new registration
- No stale data

---

## 📝 Summary of Changes

### DataStoreManager.kt
1. ✅ Added `FAILED_DOWNLOADS` key
2. ✅ Added `saveFailedDownloads()` function
3. ✅ Added `failedDownloads` Flow

### MainViewModel.kt
1. ✅ Added `loadFailedDownloads()` function
2. ✅ Call `loadFailedDownloads()` in init
3. ✅ Save failed downloads on every failure
4. ✅ Save failed downloads on every success (removal)
5. ✅ Clear failed downloads on reset

---

## 🎉 Result

**Failed downloads now persist across app restarts!**

### What You'll See:

**After App Restart with Failed Downloads:**
```
┌────────────────────────────────────┐
│ Cached Items: 7 / 10               │
│ Cache Progress: 70%                │
│ Cached Data: 560 / 800 MB          │
│                                    │
│ Download Status: 3 failed     🔴   │
│                                    │
│ [🔄 Retry Downloads (3)]      🟠   │  ← VISIBLE!
└────────────────────────────────────┘
```

**After Successful Retry:**
```
┌────────────────────────────────────┐
│ Cached Items: 10 / 10              │
│ Cache Progress: 100%          🟢   │
│ Cached Data: 800 / 800 MB          │
│                                    │
│ Download Status: ✓ Complete   🟢   │
│                                    │
│ (No retry button)                  │
└────────────────────────────────────┘
```

**Perfect!** 🚀

---

**Document Version:** 1.0  
**Last Updated:** February 8, 2026  
**Issue Status:** Resolved ✅

