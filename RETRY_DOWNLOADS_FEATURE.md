# Download Retry Feature - Implementation Summary

**Date:** February 8, 2026  
**Feature:** Automatic failed download tracking with manual retry capability  
**Status:** ✅ Complete

---

## 🎯 Problem Solved

Previously, when media downloads failed during the initial caching process:
- ❌ App silently continued without the failed items
- ❌ No way to know which downloads failed
- ❌ No way to retry without restarting the app
- ❌ Users would see playback errors without understanding why

---

## ✨ Solution Implemented

### 1. **Failed Download Tracking**

Added state management to track which items failed to download:

```kotlin
// MainViewModel.kt
private val _failedDownloads = MutableStateFlow<Set<String>>(emptySet())
val failedDownloads: StateFlow<Set<String>> = _failedDownloads.asStateFlow()
```

**How it works:**
- Every download attempt is monitored
- Failed item IDs are automatically added to the `failedDownloads` set
- Successful downloads (including retries) remove items from the set
- Set is cleared on app reset/deregistration

---

### 2. **Retry State Management**

Added a flag to track when retry is in progress:

```kotlin
// MainViewModel.kt
private val _isRetrying = MutableStateFlow(false)
val isRetrying: StateFlow<Boolean> = _isRetrying.asStateFlow()
```

**Purpose:**
- Prevents multiple concurrent retry operations
- Controls UI button visibility (button hidden during retry)
- Provides user feedback about ongoing retry

---

### 3. **Retry Function**

Implemented `retryFailedDownloads()` in `MainViewModel`:

```kotlin
fun retryFailedDownloads() {
    // 1. Check if in Playing state
    // 2. Verify there are failed downloads
    // 3. Prevent concurrent retries
    // 4. Set isRetrying = true
    // 5. Attempt to download each failed item
    // 6. Update progress after each attempt
    // 7. Remove successful downloads from failed set
    // 8. Set isRetrying = false
    // 9. Log results
}
```

**Features:**
- ✅ Retries only failed items (doesn't re-download successful ones)
- ✅ Updates cache progress in real-time
- ✅ Detailed logging for debugging
- ✅ Handles exceptions gracefully
- ✅ Works while playlist is playing

---

### 4. **Smart UI Button**

Added conditional "Retry Downloads" button in `StatsScreen`:

```kotlin
// Button appears only when:
if (failedDownloadCount > 0 && !isRetrying) {
    Button(onClick = onRetryDownloads) {
        Text("🔄 Retry Downloads ($failedDownloadCount)")
    }
}
```

**Behavior:**
- ✅ **Shows** when: Downloads have failed AND retry is not in progress
- ✅ **Hides** when: No failures OR retry is already running
- ✅ **Auto-updates**: Count updates as failures are resolved
- ✅ **Styled**: Orange button for visibility (between "Back" and "Settings")

---

### 5. **Visual Status Indicators**

Enhanced stats display with download status:

```kotlin
// Download Status field shows:
- "Retrying (N items)..." - Orange, when retry is active
- "N failed" - Red, when downloads have failed
- "In Progress..." - Blue, when initial caching is happening
- "✓ Complete" - Green, when all downloads successful
```

**Progress Bar Color:**
- Red when there are failed downloads
- Primary color when all successful

---

## 📁 Files Modified

### 1. `MainViewModel.kt`
- ✅ Added `_failedDownloads` StateFlow
- ✅ Added `_isRetrying` StateFlow
- ✅ Modified `startCaching()` to track failures
- ✅ Added `retryFailedDownloads()` function
- ✅ Clear retry state on `resetRegistration()`

### 2. `MainActivity.kt`
- ✅ Collect `failedDownloads` state
- ✅ Collect `isRetrying` state
- ✅ Pass states to `StatsScreen`
- ✅ Wire up `onRetryDownloads` callback

### 3. `StatsScreen.kt`
- ✅ Added `failedDownloadCount` parameter
- ✅ Added `isRetrying` parameter
- ✅ Added `onRetryDownloads` callback
- ✅ Conditional retry button with smart visibility
- ✅ Enhanced download status display
- ✅ Color-coded progress bar

---

## 🎬 User Experience Flow

### Scenario 1: All Downloads Successful
```
1. Device registers and starts downloading
2. All downloads complete successfully
3. Stats screen shows: "Download Status: ✓ Complete"
4. Green progress bar at 100%
5. No retry button visible
```

### Scenario 2: Some Downloads Fail
```
1. Device starts downloading playlist
2. 3 out of 10 downloads fail (network issue)
3. Stats screen shows:
   - "Download Status: 3 failed" (RED)
   - Red progress bar at 70%
   - "🔄 Retry Downloads (3)" button appears (ORANGE)
4. User presses back button to view stats
5. User clicks "Retry Downloads"
6. Button disappears immediately (retry in progress)
7. Status changes to "Retrying (3 items)..." (ORANGE)
8. Downloads are attempted again
9. If successful:
   - Status: "✓ Complete" (GREEN)
   - Progress bar: 100% green
   - No button visible
10. If still failing:
    - Status: "N failed" (RED)
    - Button reappears with updated count
```

### Scenario 3: Retry in Progress
```
1. User clicks retry button
2. Button immediately disappears
3. Status shows "Retrying (N items)..."
4. Progress bar updates in real-time
5. User cannot trigger another retry (button hidden)
6. When complete, button reappears if still failures
```

---

## 🔍 How to Test

### Test Case 1: Simulate Download Failure
1. **Setup:** Use airplane mode after 2-3 items download
2. **Expected:** Failed items tracked, retry button appears
3. **Action:** Turn on internet, click retry
4. **Expected:** Downloads succeed, button disappears

### Test Case 2: Verify Button Visibility
1. **When all succeed:** Button should NOT appear
2. **When failures exist:** Button SHOULD appear
3. **During retry:** Button should HIDE
4. **After retry with failures:** Button should REAPPEAR

### Test Case 3: Check State Persistence
1. **Setup:** Have failed downloads
2. **Action:** Navigate away from stats and back
3. **Expected:** Button still visible with correct count
4. **Action:** Reset registration
5. **Expected:** Failed downloads cleared

---

## 📊 Logging Output

### Successful Retry
```
🔄 Starting retry for 3 failed downloads...
🔄 Retrying (1/3): video1.mp4
✅ Retry successful: video1.mp4
🔄 Retrying (2/3): video2.mp4
✅ Retry successful: video2.mp4
🔄 Retrying (3/3): video3.mp4
✅ Retry successful: video3.mp4
🏁 Retry complete: 3 succeeded, 0 still failed
🎉 All downloads successful!
```

### Partial Failure
```
🔄 Starting retry for 3 failed downloads...
🔄 Retrying (1/3): video1.mp4
✅ Retry successful: video1.mp4
🔄 Retrying (2/3): video2.mp4
❌ Retry failed: video2.mp4
🔄 Retrying (3/3): video3.mp4
✅ Retry successful: video3.mp4
🏁 Retry complete: 2 succeeded, 1 still failed
⚠️ Still have 1 failed downloads
```

---

## 🚀 Benefits

### For Users
✅ **Visibility** - Know exactly what failed and how many  
✅ **Control** - Manual retry without app restart  
✅ **Feedback** - Real-time status updates during retry  
✅ **Reliability** - Complete playlists without missing media  

### For Developers
✅ **Debugging** - Clear logs showing what failed and why  
✅ **Monitoring** - Track download success rates  
✅ **Maintenance** - Easy to extend with auto-retry logic  

### For Business
✅ **Uptime** - Devices recover from transient network issues  
✅ **Support** - Fewer support tickets about missing content  
✅ **Quality** - Complete content delivery guaranteed  

---

## 🔮 Future Enhancements

### Phase 2 Ideas
1. **Auto-Retry** - Automatically retry after network restore
2. **Retry Limit** - Max 3 automatic retries before manual required
3. **Exponential Backoff** - Wait longer between retries
4. **Notification** - Alert backend when device has persistent failures
5. **Selective Retry** - Let users choose which items to retry
6. **Download Queue** - Show progress per item during retry
7. **Bandwidth Check** - Warn if network too slow before retry

---

## 💻 Code Example: Testing Locally

```bash
# Build and install
cd DigitalSignageLV
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Monitor logs
adb logcat | grep -E "MainViewModel|StatsScreen|MediaCacheManager"

# Look for:
# - "Failed to cache: <filename>"
# - "Starting retry for N failed downloads"
# - "Retry complete: X succeeded, Y still failed"
```

---

## ✅ Verification Checklist

- [x] Failed downloads tracked in StateFlow
- [x] Retry function implemented in ViewModel
- [x] UI button shows only when appropriate
- [x] Button hides during retry
- [x] Download count updates in real-time
- [x] Progress bar reflects cache status
- [x] Color coding for different states
- [x] State cleared on registration reset
- [x] No linting errors
- [x] Detailed logging for debugging

---

## 📝 Notes

- **Thread Safety:** All StateFlow updates on main thread via ViewModel
- **Concurrency:** Retry prevented when already in progress
- **Memory:** Failed item IDs stored as Set (efficient lookup/deduplication)
- **Performance:** Only retries failed items, skips successful ones
- **UX:** Button placement between primary actions for visibility

---

**Implementation Complete** ✅  
Ready for testing and deployment.

