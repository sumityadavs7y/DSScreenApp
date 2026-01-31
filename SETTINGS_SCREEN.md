# Settings Screen Feature

## Overview
A dedicated Settings screen has been created to provide centralized access to display and rotation controls. This screen is accessible from the Stats screen via a Settings button.

## Navigation Flow

```
PlayerScreen (Playing)
    ↓ (Press Back)
StatsScreen
    ↓ (Click Settings Button)
SettingsScreen
    ├─ Screen Rotation Controls
    └─ Display Mode Controls
```

## Access Path

1. **During Playback:**
   - Press **Back** button → Stats Screen

2. **From Stats Screen:**
   - Click **Settings** button (gray button between "Back to Playlist" and "Reset Registration")
   - Opens Settings Screen

3. **From Settings Screen:**
   - Click **Back** button to return to Stats Screen

## Settings Screen Layout

```
┌─────────────────────────────────────────────────────┐
│  Settings                              [Back]        │
│  Display & Rotation Controls                        │
├─────────────────────────────────────────────────────┤
│                                                      │
│  ╔═══════════════════════════════════════╗          │
│  ║  🔄 Screen Rotation                   ║          │
│  ║  Current: Landscape (90°)             ║          │
│  ║                                       ║          │
│  ║  [Clockwise] [Anti-Clockwise] [Auto] ║          │
│  ╚═══════════════════════════════════════╝          │
│                                                      │
│  ╔═══════════════════════════════════════╗          │
│  ║  📺 Display Mode                      ║          │
│  ║  Current: Fit - Keep aspect ratio     ║          │
│  ║                                       ║          │
│  ║  [FIT] [FILL] [STRETCH]              ║          │
│  ╚═══════════════════════════════════════╝          │
│                                                      │
└─────────────────────────────────────────────────────┘
```

## Features

### Screen Rotation Section
- **Blue Buttons** - Easy identification
- **Three Options:**
  - **Clockwise** (↻) - Rotate 90° clockwise
  - **Anti-Clockwise** (↺) - Rotate 90° counter-clockwise
  - **Auto** (⟳) - Follow device sensor (highlighted green when active)
- **Current Status Display** - Shows active rotation angle
- **Instant Application** - Changes apply immediately

### Display Mode Section
- **Purple Buttons** - Distinct color coding
- **Three Modes:**
  - **FIT** (📏) - Keep aspect ratio (highlighted purple when active)
  - **FILL** (🖼️) - Fill screen with cropping (highlighted purple when active)
  - **STRETCH** (⤢) - Stretch to fill (highlighted purple when active)
- **Current Status Display** - Shows active display mode
- **Real-time Preview** - Changes reflect in playlist playback

## Button States

### Rotation Buttons
| Button | Default State | Focused State | Active (Auto) |
|--------|--------------|---------------|---------------|
| Clockwise | Blue (#1976D2) | Lighter Blue (#2196F3) | N/A |
| Anti-Clockwise | Blue (#1976D2) | Lighter Blue (#2196F3) | N/A |
| Auto | Gray (#424242) / Green (#388E3C) | Lighter Gray (#616161) / Lighter Green (#4CAF50) | Green when active |

### Display Mode Buttons
| Button | Default State | Focused State | Active |
|--------|--------------|---------------|--------|
| FIT | Gray (#424242) | Lighter Gray (#616161) | Purple (#9C27B0) |
| FILL | Gray (#424242) | Lighter Gray (#616161) | Purple (#9C27B0) |
| STRETCH | Gray (#424242) | Lighter Gray (#616161) | Purple (#9C27B0) |

## Stats Screen Updates

### New Button Layout
```
┌──────────────────────────────────────────────────────────┐
│  [Back to Playlist]  [Settings]  [Reset Registration]    │
└──────────────────────────────────────────────────────────┘
```

### Button Details
- **Settings Button:**
  - Color: Gray (#424242)
  - Focused: Lighter Gray (#616161)
  - Icon: ⚙️ Settings icon
  - Position: Between "Back to Playlist" and "Reset Registration"

## Technical Implementation

### New Files
1. **`SettingsScreen.kt`** (`ui/settings/`)
   - Composable UI for settings
   - Handles rotation and display mode controls
   - Manages state and callbacks

### Modified Files
1. **`StatsScreen.kt`**
   - Removed rotation and display mode sections
   - Added Settings button
   - Simplified function signature (removed rotation/display callbacks)

2. **`MainActivity.kt`**
   - Added `showSettings` state variable
   - Updated navigation logic with `when` block
   - Added Settings screen rendering
   - Maintains rotation and display mode callbacks

### State Management

**Navigation States:**
```kotlin
// MainActivity.kt
var showStats by remember { mutableStateOf(false) }
var showSettings by remember { mutableStateOf(false) }
```

**Screen Transitions:**
```kotlin
PlayerScreen → (Back) → StatsScreen → (Settings) → SettingsScreen
                ↑                                         ↓
                └────────────────(Back)───────────────────┘
```

## Usage Examples

### Example 1: Change Rotation
1. During playback, press **Back**
2. Click **Settings** button
3. Click **Clockwise** or **Anti-Clockwise**
4. Click **Back** to return to Stats
5. Click **Back to Playlist** to resume playback
6. → Content displays with new rotation

### Example 2: Change Display Mode
1. During playback, press **Back**
2. Click **Settings** button
3. Click **FIT**, **FILL**, or **STRETCH**
4. Click **Back** to return to Stats
5. Click **Back to Playlist** to resume playback
6. → Content displays with new scaling mode

### Example 3: Reset to Auto Rotation
1. During playback, press **Back**
2. Click **Settings** button
3. Click **Auto** button (turns green)
4. Click **Back** twice to return to playback
5. → Device controls rotation based on sensor

## Advantages of Dedicated Settings Screen

### User Experience
- ✅ **Cleaner Stats Screen** - Less clutter, focused on statistics
- ✅ **Dedicated Space** - More room for settings controls
- ✅ **Logical Organization** - Settings grouped separately from stats
- ✅ **Better Navigation** - Clear separation of concerns

### Maintainability
- ✅ **Modular Code** - Settings in separate file
- ✅ **Easier Updates** - Can add more settings without cluttering Stats
- ✅ **Reusable Component** - Settings screen can be accessed from multiple places
- ✅ **Clear Responsibilities** - Stats for viewing, Settings for configuring

### Future Expansion
- ✅ **Add More Settings** - Brightness, volume, sleep timer, etc.
- ✅ **Categorization** - Can add tabs or sections for different setting types
- ✅ **Search/Filter** - Can add search functionality for many settings
- ✅ **Import/Export** - Can add settings backup/restore features

## Developer Notes

### Adding New Settings

To add a new setting to the Settings screen:

1. **Add State Management:**
```kotlin
// In MainActivity.kt
private val _newSetting = MutableStateFlow("DEFAULT")
val newSetting: StateFlow<String> = _newSetting.asStateFlow()
```

2. **Add DataStore Persistence:**
```kotlin
// In DataStoreManager.kt
val NEW_SETTING = stringPreferencesKey("new_setting")
val newSetting: Flow<String?> = ...
suspend fun saveNewSetting(value: String) { ... }
```

3. **Update SettingsScreen:**
```kotlin
// In SettingsScreen.kt
@Composable
fun SettingsScreen(
    // ... existing params
    newSetting: String,
    onSetNewSetting: (String) -> Unit
) {
    // Add new setting section UI
}
```

4. **Wire in MainActivity:**
```kotlin
// In MainActivity.kt
SettingsScreen(
    // ... existing params
    newSetting = newSettingValue,
    onSetNewSetting = { value -> setNewSetting(value) }
)
```

### Customization

**Change Button Colors:**
```kotlin
// In SettingsScreen.kt
colors = ButtonDefaults.colors(
    containerColor = Color(0xFFYOURCOLOR),
    focusedContainerColor = Color(0xFFYOURFOCUSCOLOR)
)
```

**Adjust Layout Width:**
```kotlin
// In SettingsScreen.kt
Surface(
    modifier = Modifier.width(600.dp), // Change width
    // ...
)
```

**Change Button Height:**
```kotlin
// In SettingsScreen.kt
Button(
    modifier = Modifier.height(80.dp), // Change height
    // ...
)
```

## Testing

### Manual Testing Checklist

- [ ] Settings button visible on Stats screen
- [ ] Settings button opens Settings screen
- [ ] Back button returns to Stats screen
- [ ] All rotation buttons work correctly
- [ ] All display mode buttons work correctly
- [ ] Settings persist across app restart
- [ ] Settings persist across device reboot
- [ ] Visual feedback shows current active settings
- [ ] No layout issues on different screen sizes
- [ ] Focus navigation works correctly with remote

### Test Scenarios

**Scenario 1: Navigation Flow**
```
1. Start playback
2. Press Back → Stats screen appears
3. Click Settings → Settings screen appears
4. Click Back → Stats screen appears
5. Click Back to Playlist → Playback resumes
✓ Pass if all transitions work smoothly
```

**Scenario 2: Setting Persistence**
```
1. Open Settings
2. Change rotation to 90°
3. Change display mode to FILL
4. Return to playback
5. Force close app
6. Reopen app
7. Open Settings
✓ Pass if rotation shows 90° and display mode shows FILL
```

**Scenario 3: Visual Feedback**
```
1. Open Settings
2. Observe Auto button (green if active, gray if not)
3. Observe active display mode button (purple if active, gray if not)
4. Click different buttons
5. Verify active button changes color
✓ Pass if visual feedback is clear and immediate
```

## Logging

Monitor Settings screen activity:

```bash
adb logcat | findstr "Settings\|Rotation\|Display mode"
```

Expected output:
```
MainActivity: 📺 Loading saved display mode: FIT
MainActivity: 🔄 Loading saved rotation: 90
MainActivity: 🔄 Rotating to: 180
MainActivity: ✅ Screen rotation saved: 180
MainActivity: 📺 Setting display mode to: FILL
MainActivity: ✅ Display mode saved: FILL
```

## Troubleshooting

### Settings Button Not Visible
**Issue**: Can't find Settings button on Stats screen
**Solution**: Look between "Back to Playlist" and "Reset Registration" buttons at the top

### Settings Not Persisting
**Issue**: Settings reset after app restart
**Solution**: 
- Check DataStore permissions
- Verify app has storage access
- Check logs for save errors

### Navigation Issues
**Issue**: Can't return from Settings screen
**Solution**: Use Back button on remote or touch/click Back button in UI

### Focus Issues
**Issue**: Can't navigate with remote
**Solution**: 
- Ensure TV remote mode is enabled
- Check if focus requester is working
- Verify button focus states

## Version History

- **v1.0** (2026-01-31): Initial Settings screen implementation
  - Dedicated settings screen created
  - Rotation and display mode controls moved from Stats
  - Settings button added to Stats screen
  - Clean separation of concerns

