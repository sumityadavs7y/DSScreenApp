# Custom Display Mode Feature

## Overview
The Custom Display Mode feature allows you to set different display modes (FIT, FILL, STRETCH) for each individual item in your playlist. This is perfect when you have mixed content with different aspect ratios that need different handling.

## Navigation Flow

```
PlayerScreen
    ↓ (Press Back)
StatsScreen
    ↓ (Click Settings)
SettingsScreen
    ↓ (Click CUSTOM Button)
CustomDisplayModeScreen
    └─ List of all playlist items
       └─ Each item has [FIT] [FILL] [STRETCH] buttons
```

## How to Use

### Step 1: Enable Custom Mode

1. **During Playback:**
   - Press **Back** → Stats Screen
   
2. **Open Settings:**
   - Click **⚙️ Settings** button
   
3. **Enable Custom Mode:**
   - In Display Mode section, click **CUSTOM - Per Item Settings** (orange button)
   - This opens the Custom Display Mode Screen

### Step 2: Configure Each Item

1. **View Playlist Items:**
   - All items are listed with their name, duration, and type (video/image)
   
2. **Set Display Mode Per Item:**
   - For each item, choose:
     - **FIT** - Keep aspect ratio, may show black bars
     - **FILL** - Fill screen, may crop edges
     - **STRETCH** - Stretch to fill, may distort
   
3. **Visual Feedback:**
   - Active mode for each item is highlighted in purple
   - Each item's setting is saved immediately
   
4. **Return:**
   - Click **Back** to return to Settings
   - Click **Back** again to return to Stats
   - Click **Back to Playlist** to resume playback

### Step 3: Playback

- When playback resumes, each item displays using its custom mode
- Settings persist across app restarts and device reboots

## Use Cases

### Example 1: Mixed Aspect Ratio Content

**Scenario:** Playlist with 16:9 landscape videos and 9:16 portrait videos

```
Playlist:
├─ landscape1.mp4 (16:9) → Set to FILL
├─ landscape2.mp4 (16:9) → Set to FILL  
├─ portrait1.mp4 (9:16) → Set to FIT
├─ portrait2.mp4 (9:16) → Set to FIT
└─ landscape3.mp4 (16:9) → Set to FILL

Result:
→ Landscape videos fill the screen nicely
→ Portrait videos show completely without distortion
```

### Example 2: Text vs. Media Content

**Scenario:** Mix of text slides and photos

```
Playlist:
├─ announcement.jpg (Text-heavy) → Set to FIT
├─ photo1.jpg (Photo) → Set to FILL
├─ schedule.jpg (Text-heavy) → Set to FIT
├─ photo2.jpg (Photo) → Set to FILL
└─ menu.jpg (Text-heavy) → Set to FIT

Result:
→ Text slides show fully (readable, not distorted)
→ Photos fill screen (maximize visual impact)
```

### Example 3: Logo vs. Product Images

**Scenario:** Company signage with branding and products

```
Playlist:
├─ company_logo.png (Square) → Set to FIT
├─ product1.jpg (Various) → Set to FILL
├─ company_logo.png (Square) → Set to FIT
├─ product2.jpg (Various) → Set to FILL
└─ contact_info.jpg (Wide) → Set to STRETCH

Result:
→ Logo maintains proper aspect ratio
→ Products fill screen attractively
→ Contact info stretches to use full width
```

## Features

### Per-Item Configuration
- ✅ **Individual Settings** - Each playlist item has its own display mode
- ✅ **Visual Indicators** - Video icon (blue) vs Image icon (purple)
- ✅ **Item Information** - Shows filename and duration
- ✅ **Instant Feedback** - Active mode highlighted immediately

### Persistence
- ✅ **Auto-Save** - Each change saved instantly
- ✅ **Survives Restart** - Settings persist across app restarts
- ✅ **Survives Reboot** - Settings persist across device reboots
- ✅ **Per-Device** - Each device can have different custom settings

### UI Design
- ✅ **Clean Layout** - Scrollable list of all items
- ✅ **Easy Navigation** - Large touch targets for TV remotes
- ✅ **Color Coding** - Purple for active, gray for inactive
- ✅ **Icon Indicators** - Visual distinction between media types

## Technical Details

### Architecture

**Data Flow:**
```
CustomDisplayModeScreen (UI)
    ↓ (onSetItemDisplayMode)
MainActivity.setItemDisplayMode()
    ↓ (save to storage)
DataStoreManager.saveItemDisplayMode()
    ↓ (stored as JSON)
Android DataStore
    ↓ (load on startup)
MainActivity.loadCustomDisplayModes()
    ↓ (pass to player)
PlayerScreen
    ↓ (lookup per item)
ImagePlayer / VideoPlayer
```

### Storage Format

**DataStore Key:** `"custom_display_modes"`

**Format:** JSON String
```json
{
  "item-123": "FIT",
  "item-456": "FILL",
  "item-789": "STRETCH"
}
```

**Example:**
```json
{
  "660a1234567890abcdef1234": "FILL",
  "660a1234567890abcdef5678": "FIT",
  "660a1234567890abcdef9012": "STRETCH"
}
```

### Implementation Files

**New Files:**
1. **`CustomDisplayModeScreen.kt`**
   - UI for per-item display mode configuration
   - Lists all playlist items
   - Shows FIT/FILL/STRETCH buttons for each item
   - Handles item display mode changes

**Modified Files:**
1. **`DataStoreManager.kt`**
   - Added `CUSTOM_DISPLAY_MODES` preference key
   - Added `customDisplayModes` Flow property
   - Added `saveCustomDisplayModes()` method
   - Added `saveItemDisplayMode()` method
   - Added JSON parsing/serialization helpers

2. **`SettingsScreen.kt`**
   - Added `onOpenCustomDisplayMode` callback
   - Added CUSTOM button (orange)
   - Updated layout to accommodate 4 display mode buttons

3. **`MainActivity.kt`**
   - Added `_customDisplayModes` StateFlow
   - Added `showCustomDisplayMode` navigation state
   - Added `loadCustomDisplayModes()` method
   - Added `setItemDisplayMode()` method
   - Updated navigation logic for CustomDisplayModeScreen
   - Passes custom display modes to PlayerScreen

4. **`PlayerScreen.kt`**
   - Added `customDisplayModes` parameter
   - Determines actual display mode based on mode setting:
     - If `"CUSTOM"` → lookup item-specific mode
     - Otherwise → use global mode
   - Passes resolved mode to ImagePlayer/VideoPlayer

### State Management

**Global Display Mode:**
```kotlin
private val _currentDisplayMode = MutableStateFlow("FIT")
// Values: "FIT", "FILL", "STRETCH", "CUSTOM"
```

**Custom Display Modes (Per Item):**
```kotlin
private val _customDisplayModes = MutableStateFlow<Map<String, String>>(emptyMap())
// Keys: Item IDs, Values: "FIT", "FILL", "STRETCH"
```

**Mode Resolution Logic:**
```kotlin
val actualDisplayMode = if (displayMode == "CUSTOM") {
    customDisplayModes[currentItem.id] ?: "FIT"  // Default to FIT if not set
} else {
    displayMode  // Use global mode
}
```

## UI Design

### Custom Display Mode Screen Layout

```
┌──────────────────────────────────────────────────────────────┐
│  Custom Display Modes                            [Back]       │
│  Set display mode for each item                               │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ 🎬 video_landscape.mp4 (30s)   [FIT] [FILL] [STRETCH]│  │
│  └────────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ 📷 image_portrait.jpg (15s)    [FIT] [FILL] [STRETCH]│  │
│  └────────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ 🎬 video_square.mp4 (20s)      [FIT] [FILL] [STRETCH]│  │
│  └────────────────────────────────────────────────────────┘  │
│                                                               │
│  ... (scrollable list continues)                             │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### Button Design

**CUSTOM Button (SettingsScreen):**
- **Default Color:** Gray (#424242)
- **Active Color:** Orange (#FF6F00)
- **Focused Active:** Lighter Orange (#FF8F00)
- **Icon:** 🎛️ Tune icon
- **Text:** "CUSTOM - Per Item Settings"
- **Layout:** Full width, larger than other buttons

**Per-Item Buttons (CustomDisplayModeScreen):**
- **Size:** 90dp × 50dp
- **Spacing:** 8dp between buttons
- **Active Color:** Purple (#9C27B0)
- **Inactive Color:** Gray (#424242)
- **Icons:** Same as global mode (FitScreen, Fullscreen, CropFree)

## Workflow Examples

### Workflow 1: Enable Custom Mode

```
1. Playback running
2. Press Back → Stats Screen
3. Click Settings → Settings Screen
4. Click CUSTOM button (turns orange)
5. Custom Display Mode Screen opens
6. Configure each item
7. Click Back → Settings Screen (CUSTOM still orange)
8. Click Back → Stats Screen
9. Click Back to Playlist → Playback resumes
10. Each item uses its custom display mode ✅
```

### Workflow 2: Modify Custom Settings

```
1. CUSTOM mode already active
2. Press Back → Stats Screen
3. Click Settings → Settings Screen
4. Click CUSTOM button → Custom Display Mode Screen
5. Change display mode for specific items
6. Click Back twice → Playback resumes
7. Updated settings applied immediately ✅
```

### Workflow 3: Switch Back to Global Mode

```
1. CUSTOM mode active
2. Press Back → Stats Screen
3. Click Settings → Settings Screen
4. Click FIT, FILL, or STRETCH button
5. Custom settings preserved but not used
6. All items now use the selected global mode ✅
7. Can re-enable CUSTOM anytime to restore per-item settings
```

## Advantages

### Flexibility
- **Mixed Content Support** - Handle different aspect ratios properly
- **Content-Aware Display** - Text vs media can use different modes
- **Per-Item Control** - Fine-grained customization
- **Easy Switching** - Can toggle between CUSTOM and global modes

### User Experience
- **Visual Clarity** - Clear indication of which mode is active for each item
- **Simple Interface** - Easy to understand and use
- **Instant Feedback** - Changes reflected immediately
- **Persistent Settings** - No need to reconfigure after restart

### Professional Use
- **Optimal Presentation** - Each content type displays optimally
- **Brand Consistency** - Logos maintain aspect ratio
- **Content Integrity** - Text remains readable
- **Visual Appeal** - Photos and videos fill screen when appropriate

## Logging

Monitor custom display mode activity:

```bash
adb logcat | findstr "Custom\|Display mode\|PlayerScreen"
```

Expected output:
```
MainActivity: 📺 Loading custom display modes: 5 items
MainActivity: 📺 Setting display mode to: CUSTOM
MainActivity: ✅ Display mode saved: CUSTOM
MainActivity: 📺 Setting display mode for item 660a1234567890abcdef1234 to: FILL
MainActivity: ✅ Item display mode saved: 660a1234567890abcdef1234 = FILL
PlayerScreen: Using custom display mode for item 660a1234567890abcdef1234: FILL
ImagePlayer: 🖼️ Displaying image: photo.jpg, Mode: FILL
```

## Troubleshooting

### Custom Settings Not Saving
**Issue:** Changes don't persist after restart
**Solution:**
- Check DataStore permissions
- Verify storage access
- Check logs for JSON parsing errors
- Ensure item IDs are consistent

### Items Using Wrong Display Mode
**Issue:** Item displays with unexpected mode
**Solution:**
- Verify CUSTOM mode is active (orange button in Settings)
- Check custom settings for that specific item
- Ensure item ID matches (check logs)
- Try re-setting the item's display mode

### Custom Mode Not Activating
**Issue:** Clicking CUSTOM button doesn't work
**Solution:**
- Ensure playlist is loaded
- Check that CustomDisplayModeScreen is accessible
- Verify navigation state in MainActivity
- Check logs for errors

### Default Mode Used Instead of Custom
**Issue:** All items use FIT mode in CUSTOM mode
**Solution:**
- This is expected if no custom settings configured yet
- Configure each item's display mode in Custom Display Mode Screen
- Changes save automatically

## Best Practices

### Content Preparation
1. **Audit Your Content** - Review all items and their aspect ratios
2. **Categorize** - Group similar content (text, photos, videos, etc.)
3. **Set Strategy** - Decide which items need which modes
4. **Configure Once** - Set up custom modes and they persist forever

### Mode Selection Guidelines

**Use FIT for:**
- Text-heavy content (menus, schedules, announcements)
- Logos and branding
- Content where aspect ratio is critical
- Square images on landscape displays

**Use FILL for:**
- Photos where edges can be cropped
- Videos where full-screen is desired
- Content where minor cropping is acceptable
- Background visuals

**Use STRETCH for:**
- Graphics specifically designed for your display
- Content where distortion is minimal or acceptable
- Full-width/height requirements
- Last resort for problematic aspect ratios

### Maintenance
- **Test After Changes** - Preview playlist after configuring custom modes
- **Document Settings** - Keep notes on which items use which modes
- **Periodic Review** - Check if custom settings still work as content changes
- **Backup** - Consider documenting custom settings for recovery

## Future Enhancements

Possible improvements:
- [ ] Bulk edit - Set mode for multiple items at once
- [ ] Templates - Save/load custom mode profiles
- [ ] Preview - Show thumbnail with selected display mode
- [ ] Import/Export - Backup and restore custom settings
- [ ] Smart suggestions - AI-based mode recommendations
- [ ] Content analysis - Auto-detect best mode per item
- [ ] Rotation + Display - Per-item rotation settings too

## Comparison: Global vs Custom Modes

| Feature | Global Mode | Custom Mode |
|---------|-------------|-------------|
| **Applies To** | All items | Each item individually |
| **Setup** | 1 click | Configure each item |
| **Flexibility** | Low | High |
| **Best For** | Uniform content | Mixed content |
| **Persistence** | Yes | Yes |
| **Ease of Use** | Very Easy | Moderate |
| **Results** | Consistent | Optimized per item |

## Version History

- **v1.0** (2026-01-31): Initial Custom Display Mode implementation
  - Per-item display mode configuration
  - CUSTOM mode button in Settings
  - CustomDisplayModeScreen with item list
  - JSON-based persistence in DataStore
  - Integration with PlayerScreen
  - Orange color coding for CUSTOM mode

