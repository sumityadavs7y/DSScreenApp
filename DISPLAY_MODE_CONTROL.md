# Display Mode Control Feature

## Overview
This feature allows users to control how media content (images and videos) is displayed on the screen through the Stats screen. The setting is persisted across app restarts and device reboots.

## Display Modes

### 1. **FIT** (Default)
- **Description**: Keeps the original aspect ratio of the content
- **Behavior**: Content fits inside the screen with no cropping or stretching
- **Result**: May show black bars (letterbox/pillarbox) if aspect ratios don't match
- **Use Case**: When you want to see the entire content without distortion
- **Icon**: 📏 FitScreen

### 2. **FILL**
- **Description**: Keeps aspect ratio but fills the entire screen
- **Behavior**: Content is scaled to fill the screen, cropping edges if needed
- **Result**: No black bars, but some content may be cut off at edges
- **Use Case**: When you want to maximize screen usage without distortion
- **Icon**: 🖼️ Fullscreen

### 3. **STRETCH**
- **Description**: Ignores aspect ratio to fill the screen
- **Behavior**: Content is stretched/squished to exactly match screen dimensions
- **Result**: No black bars, no cropping, but may appear distorted
- **Use Case**: When you want to fill the entire screen regardless of distortion
- **Icon**: ⤢ CropFree

## How to Use

### Accessing Display Mode Controls

1. **During Playlist Playback:**
   - Press the **Back** button on your remote
   - This will navigate to the Stats Screen

2. **On Stats Screen:**
   - Scroll down to see the **Display Mode** section (purple buttons)
   - Located below the **Screen Rotation** section (blue buttons)

3. **Select a Mode:**
   - Use your remote to navigate to the desired button
   - Click **FIT**, **FILL**, or **STRETCH**
   - The active mode is highlighted in purple

4. **Return to Playback:**
   - Click "Back to Playlist" button
   - Content immediately plays with the new display mode

### Example Workflows

#### Portrait Display (Digital Signage in Portrait Orientation)
```
Screen Rotation: 0° (Portrait)
Display Mode: FIT or FILL
→ Content displays properly in portrait orientation
```

#### Landscape Display (Standard TV/Monitor)
```
Screen Rotation: 90° (Landscape) or AUTO
Display Mode: FIT or FILL
→ Content displays properly in landscape orientation
```

#### Mixed Aspect Ratio Content
```
Playlist: Mix of 16:9, 4:3, and portrait videos
Display Mode: FIT
→ All content shows fully, may have black bars
```

```
Playlist: Mix of 16:9, 4:3, and portrait videos
Display Mode: FILL
→ All content fills screen, may crop edges
```

## Technical Details

### Architecture

**Data Flow:**
```
StatsScreen (UI)
    ↓ (onSetDisplayMode callback)
MainActivity.setDisplayMode()
    ↓ (update StateFlow)
_currentDisplayMode.value = mode
    ↓ (save to storage)
DataStoreManager.saveDisplayMode()
    ↓ (pass to player)
PlayerScreen (displayMode parameter)
    ↓ (apply to components)
ImagePlayer & VideoPlayer
```

### Implementation

**Images (Coil AsyncImage):**
```kotlin
val contentScale = when (displayMode) {
    "FIT" -> ContentScale.Fit
    "FILL" -> ContentScale.Crop
    "STRETCH" -> ContentScale.FillBounds
    else -> ContentScale.Fit
}

AsyncImage(
    model = imageUrl,
    contentScale = contentScale,
    // ...
)
```

**Videos (ExoPlayer PlayerView):**
```kotlin
val playerResizeMode = when (displayMode) {
    "FIT" -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    "FILL" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    "STRETCH" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

PlayerView(context).apply {
    resizeMode = playerResizeMode
    // ...
}
```

### Persistence

**Storage:**
- Uses Android DataStore (Preferences)
- Key: `"display_mode"`
- Values: `"FIT"`, `"FILL"`, `"STRETCH"`
- Default: `"FIT"`

**Lifecycle:**
1. **onCreate()**: Loads saved display mode from DataStore
2. **User Interaction**: Updates StateFlow and saves to DataStore
3. **App Restart/Reboot**: Loads saved mode automatically

### State Management

**StateFlow in MainActivity:**
```kotlin
private val _currentDisplayMode = MutableStateFlow("FIT")
val currentDisplayMode: StateFlow<String> = _currentDisplayMode.asStateFlow()
```

**UI Collection:**
```kotlin
val currentDisplayModeValue by currentDisplayMode.collectAsState()
```

## Files Modified

### Core Files
1. **`MainActivity.kt`**
   - Added `_currentDisplayMode` StateFlow
   - Added `loadSavedDisplayMode()` method
   - Added `setDisplayMode()` method
   - Passed `displayMode` to `PlayerScreen` and `currentDisplayMode` to `StatsScreen`

2. **`DataStoreManager.kt`**
   - Added `DISPLAY_MODE` preference key
   - Added `displayMode` Flow property
   - Added `saveDisplayMode()` method

3. **`StatsScreen.kt`**
   - Added `currentDisplayMode` parameter
   - Added `onSetDisplayMode` callback
   - Added Display Mode section UI with 3 buttons
   - Added `getDisplayModeLabel()` helper function

4. **`PlayerScreen.kt`**
   - Added `displayMode` parameter
   - Passed `displayMode` to `ImagePlayer` and `VideoPlayer`

5. **`ImagePlayer` (in PlayerScreen.kt)**
   - Added `displayMode` parameter
   - Maps display mode to `ContentScale`
   - Applies `contentScale` to `AsyncImage`

6. **`VideoPlayer` (in PlayerScreen.kt)**
   - Added `displayMode` parameter
   - Maps display mode to ExoPlayer resize mode
   - Applies `resizeMode` to `PlayerView`

## UI Design

### Stats Screen Layout
```
┌───────────────────────────────────────┐
│  Left Column (Scrollable)             │
│  ├─ Remote Management                 │
│  ├─ Total Items                       │
│  ├─ Offline Ready                     │
│  │                                    │
│  ├─ 🔄 Screen Rotation                │
│  │  └─ [CW] [CCW] [AUTO] (Blue)      │
│  │                                    │
│  └─ 📺 Display Mode                   │
│     └─ [FIT] [FILL] [STRETCH] (Purple)│
└───────────────────────────────────────┘
```

### Button Design
- **Container**: Dark card (Color: #1E1E1E)
- **Active Button**: Purple (#9C27B0)
- **Inactive Button**: Gray (#424242)
- **Focused Active**: Lighter Purple (#AB47BC)
- **Focused Inactive**: Lighter Gray (#616161)
- **Icon Size**: 24dp
- **Button Height**: 60dp
- **Layout**: Icon above text in a column

## Logging

### Debug Output
Monitor display mode changes with:
```bash
adb logcat | findstr "Display mode\|ImagePlayer\|VideoPlayer"
```

### Expected Log Output
```
MainActivity: 📺 Loading saved display mode: FIT
MainActivity: 📺 Setting display mode to: FILL
MainActivity: ✅ Display mode saved: FILL
ImagePlayer: 🖼️ Displaying image: photo.jpg, Mode: FILL, Local: true
VideoPlayer: 🎥 Initializing video: video.mp4, Mode: FILL, Local: true
```

## Testing

### Manual Testing Steps

1. **Test Default Mode:**
   - Fresh install or after reset
   - Verify FIT mode is active (purple highlight)
   - Verify content displays with aspect ratio preserved

2. **Test Mode Changes:**
   - Switch to FILL mode
   - Verify content fills screen (may crop)
   - Switch to STRETCH mode
   - Verify content stretches to fill (may distort)
   - Switch back to FIT mode
   - Verify content shows with aspect ratio again

3. **Test Persistence:**
   - Set mode to FILL
   - Force close app
   - Reopen app
   - Verify FILL mode is still active
   - Verify content displays in FILL mode

4. **Test After Reboot:**
   - Set mode to STRETCH
   - Reboot device
   - Wait for app to auto-start
   - Verify STRETCH mode is still active

5. **Test with Different Content:**
   - Create playlist with mixed aspect ratios:
     - 16:9 videos (landscape)
     - 4:3 videos (classic TV)
     - 9:16 videos (portrait)
   - Test each display mode
   - Verify behavior matches expectations

6. **Test Combined with Rotation:**
   - Set rotation to 90° (Landscape)
   - Set display mode to FIT
   - Verify content displays correctly
   - Set rotation to 0° (Portrait)
   - Verify content adjusts to portrait FIT
   - Try all combinations of rotation and display modes

## Troubleshooting

### Display Mode Not Persisting
**Symptom**: Mode resets to FIT after restart
**Solution**: 
- Check DataStore permissions
- Verify app has storage access
- Check logs for save errors

### Content Not Displaying Correctly
**Symptom**: Content appears distorted or cropped unexpectedly
**Solution**:
- Verify correct display mode is active in Stats screen
- Check content original aspect ratio
- Try different display modes to find best fit

### Buttons Not Visible
**Symptom**: Cannot see display mode buttons on Stats screen
**Solution**:
- Scroll down on the Stats screen (left column is scrollable)
- Look below the Screen Rotation section
- Check if screen is too small (may need to scroll)

## Comparison with Screen Rotation

| Feature | Screen Rotation | Display Mode |
|---------|----------------|--------------|
| **Purpose** | Change screen orientation | Change content scaling |
| **Options** | Clockwise, Anti-Clockwise, Auto | FIT, FILL, STRETCH |
| **Color** | Blue buttons | Purple buttons |
| **Affects** | Device orientation | Content aspect ratio |
| **Persistence** | Yes | Yes |
| **Location** | Stats Screen | Stats Screen (below rotation) |

## Best Practices

1. **For Professional Signage:**
   - Use FIT mode to avoid distortion
   - Ensure content matches display aspect ratio
   - Test all content before deployment

2. **For Maximum Coverage:**
   - Use FILL mode to eliminate black bars
   - Accept minor edge cropping
   - Ensure critical content is centered

3. **For Full Screen (Last Resort):**
   - Use STRETCH only if absolutely needed
   - Accept aspect ratio distortion
   - Best for text-heavy content where ratio matters less

4. **Content Preparation:**
   - Create content matching your display's aspect ratio
   - Test with FIT mode first
   - Use FILL/STRETCH only when necessary

## Future Enhancements

Possible future improvements:
- [ ] Add "AUTO" mode that detects content aspect ratio
- [ ] Add numeric indicator showing detected vs display aspect ratio
- [ ] Add preview thumbnails for each mode
- [ ] Add per-content display mode override
- [ ] Add transition animations between modes

## Version History

- **v1.0** (2026-01-31): Initial implementation
  - FIT, FILL, and STRETCH modes
  - Persistence with DataStore
  - Integration with rotation controls
  - Stats screen UI with purple buttons

