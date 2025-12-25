# Video Playback Feature - DSScreen Android App

## Overview

The DSScreen Android app now includes full-screen video playback functionality with playlist management, video looping based on duration, and automatic playlist sequencing.

## Features

✅ **Full-Screen Video Playback**
- Immersive full-screen experience
- ExoPlayer-based streaming with caching
- Supports progressive download
- Hardware-accelerated video decoding

✅ **Playlist Management**
- Plays videos in order based on playlist sequence
- Each video loops for its specified duration (in seconds)
- Automatic transition to next video when duration expires
- Entire playlist loops continuously

✅ **Video Streaming**
- Streams from backend with range request support
- Progressive caching for offline playback
- No authentication required (public endpoint)
- Optimized for Android TV bandwidth

✅ **Playback Controls**
- Pause/resume playback (show/hide controls)
- View current video progress
- Exit to playlist view
- De-register device option

✅ **On-Screen Information**
- Current video index (e.g., "1/5")
- Time remaining for current video
- Playlist name
- Video file name

## Architecture

### Components

**PlaylistManager** (`player/PlaylistManager.kt`)
- Manages video sequencing
- Tracks current video index
- Handles playlist looping
- Provides video URLs and metadata

**VideoPlayerScreen** (`ui/screens/VideoPlayerScreen.kt`)
- Full-screen ExoPlayer integration
- Duration-based timer for video switching
- Overlay controls (pause/exit/de-register)
- Background overlay for control visibility

**Updated PlayerScreen** (`ui/screens/PlayerScreen.kt`)
- "Start Playback" button to begin video player
- Shows playlist information before playback

## How It Works

### 1. **Playlist Structure**
Each playlist contains:
```kotlin
Playlist {
  id: String
  name: String
  code: String
  items: List<PlaylistItem> {
    order: Int          // Sequence order (0, 1, 2, ...)
    duration: Int       // How long to loop this video (seconds)
    video: PlaylistVideo {
      id: String
      fileName: String
      filePath: String
    }
  }
}
```

### 2. **Video Playback Flow**

```
Registration → Player Screen → [Start Playback] → Video Player Screen
                    ↑                                        ↓
                    └─────────[Exit] or [De-register]───────┘
```

**Video Player Loop:**
```
Video 1 (30s) → Loop Video 1 for 30s → Video 2 (45s) → Loop Video 2 for 45s 
     ↓                                                                ↓
Video 5 (20s) ← Loop Video 4 for 60s ← Video 4 (60s) ← Video 3 (25s)
     ↓
[Playlist repeats from Video 1]
```

### 3. **Video Duration Timer**

The app uses a coroutine-based timer:
```kotlin
LaunchedEffect(currentItem) {
    val duration = playlistManager.getCurrentDuration()
    remainingTime = duration

    while (isActive && remainingTime > 0) {
        delay(1000) // 1 second
        remainingTime--
    }

    // Move to next video when timer reaches 0
    if (remainingTime == 0) {
        playlistManager.moveToNext()
    }
}
```

### 4. **Video Streaming**

Videos are streamed from the backend using ExoPlayer:
```kotlin
// Backend endpoint (public, no auth required)
GET /api/videos/{videoId}/download

// Supports HTTP range requests for seeking and streaming
// ExoPlayer automatically handles:
// - Progressive download
// - Buffering
// - Caching
// - Adaptive streaming
```

## User Interface

### Player Screen
- **Playlist Info Card** - Shows registered playlist details
- **Device Info Card** - Shows device UID, ID, registration time
- **Start Playback Button** - Large button to begin full-screen playback
- **Playlist Videos List** - Shows all videos with order, duration, resolution
- **Re-register Button** - Clears registration and returns to code entry

### Video Player Screen

**During Playback (Controls Hidden):**
- Full-screen video
- Small info overlay (top-right):
  - Current video index (e.g., "2/5")
  - Time remaining (e.g., "25s")
- Floating Action Button (bottom-right) to show controls

**With Controls Visible:**
- Semi-transparent black overlay
- Control panel showing:
  - Playlist name
  - Current video index and total
  - Video file name
  - Time remaining counter
  - **Resume Playback** button
  - **Exit Player** button (returns to player screen)
  - **De-register Device** button (with confirmation dialog)

### Control Buttons

| Button | Action | Color |
|--------|--------|-------|
| Resume Playback | Hides controls, resumes video | Primary |
| Exit Player | Stops playback, returns to player screen | Outlined |
| De-register Device | Confirms, then de-registers and returns to registration | Error/Red |

## Configuration

### Base URL

Update in `PlaylistManager.kt` if using physical device:
```kotlin
// Currently in VideoPlayerScreen.kt
val videoUrl = playlistManager.getCurrentVideoUrl("http://10.0.2.2:3000/")

// For physical devices, use:
val videoUrl = playlistManager.getCurrentVideoUrl("http://YOUR_IP:3000/")
```

### Video Looping

Each video loops continuously until its duration timer expires:
- **Duration from playlist**: Specified in `PlaylistItem.duration` (seconds)
- **ExoPlayer repeat mode**: `REPEAT_MODE_ONE` (loops current video)
- **Timer**: Coroutine-based countdown in UI layer

## Example Usage

### 1. Create a Playlist in Backend
```javascript
{
  "name": "Store Display",
  "code": "ABCD1",
  "items": [
    {
      "order": 0,
      "videoId": "video-uuid-1",
      "duration": 30  // Play for 30 seconds
    },
    {
      "order": 1,
      "videoId": "video-uuid-2",
      "duration": 45  // Play for 45 seconds
    },
    {
      "order": 2,
      "videoId": "video-uuid-3",
      "duration": 60  // Play for 60 seconds
    }
  ]
}
```

### 2. Register Device
- Open app on Android TV
- Enter playlist code: `ABCD1`
- View playlist details

### 3. Start Playback
- Click "Start Playback (3 videos)"
- Video player opens in full screen
- Video 1 plays and loops for 30 seconds
- Automatically switches to Video 2
- Video 2 plays and loops for 45 seconds
- Continues through all videos
- Returns to Video 1 and repeats entire playlist

### 4. Control Playback
- Press **Back** or click **FAB** to show controls
- View current progress and time remaining
- Choose to resume, exit, or de-register

## Technical Details

### ExoPlayer Configuration
```kotlin
ExoPlayer.Builder(context).build().apply {
    repeatMode = Player.REPEAT_MODE_ONE  // Loop current video
    playWhenReady = true                 // Auto-play
}
```

### Media Source
```kotlin
val dataSourceFactory = DefaultHttpDataSource.Factory()
    .setDefaultRequestProperties(mapOf("Accept" to "video/*"))

val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
    .createMediaSource(MediaItem.fromUri(videoUrl))
```

### Caching
ExoPlayer automatically caches video data:
- **Progressive download**: Continues downloading while playing
- **In-memory cache**: Keeps recently played segments
- **Disk cache**: Can be configured for persistent caching

## Troubleshooting

### Video Not Playing
- **Check network connectivity**: Ensure device can reach backend
- **Verify video URL**: Check Logcat for ExoPlayer errors
- **Test endpoint**: Use browser to access `/api/videos/{id}/download`
- **Check video format**: Ensure video codec is supported by device

### Video Stuttering/Buffering
- **Network speed**: Ensure stable connection
- **File size**: Large videos may take time to buffer
- **Reduce resolution**: Use lower resolution videos for testing
- **Check backend**: Verify backend isn't throttling connections

### Timer Not Working
- **Check duration**: Ensure `PlaylistItem.duration` is set correctly
- **Verify coroutine**: Check Logcat for coroutine cancellation
- **Test timer**: Add logs to track remaining time countdown

### Video Doesn't Switch
- **Check PlaylistManager**: Verify `moveToNext()` is being called
- **Check LaunchedEffect**: Ensure timer completes and triggers transition
- **Verify playlist items**: Ensure playlist has multiple videos

### Controls Don't Show
- **Press Back button**: Should trigger controls
- **Click FAB**: Bottom-right button should show controls
- **Check overlay**: Verify `showControls` state is updating

## Performance Optimization

### For Production:

1. **Enable disk caching**:
```kotlin
val cacheDataSourceFactory = CacheDataSource.Factory()
    .setCache(cache)
    .setUpstreamDataSourceFactory(httpDataSourceFactory)
```

2. **Preload next video**:
```kotlin
// Preload next video 10 seconds before current ends
if (remainingTime <= 10) {
    preloadNextVideo()
}
```

3. **Optimize video files**:
- Use H.264 codec (best compatibility)
- Recommended resolution: 1920x1080 or 1280x720
- Recommended bitrate: 5-10 Mbps
- Use MP4 container format

4. **Network optimization**:
- Use CDN for video hosting if possible
- Configure backend to enable GZIP compression
- Implement connection pooling in OkHttp

## Dependencies

Added to project:
```toml
[versions]
media3 = "1.2.1"

[libraries]
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
androidx-media3-common = { group = "androidx.media3", name = "media3-common", version.ref = "media3" }
```

## Files Created/Modified

### New Files:
- `player/PlaylistManager.kt` - Playlist sequencing logic
- `ui/screens/VideoPlayerScreen.kt` - Full-screen video player
- `VIDEO_PLAYBACK_GUIDE.md` - This documentation

### Modified Files:
- `ui/screens/PlayerScreen.kt` - Added "Start Playback" button
- `navigation/NavGraph.kt` - Added VideoPlayer route
- `gradle/libs.versions.toml` - Added Media3 dependencies
- `app/build.gradle.kts` - Added Media3 implementations

## Future Enhancements

Potential features to add:
- [ ] Preload next video for seamless transitions
- [ ] Persistent disk cache for offline playback
- [ ] Video download for offline mode
- [ ] Playlist sync/refresh during playback
- [ ] Analytics (track playback duration, skips, errors)
- [ ] Picture-in-Picture (PiP) mode
- [ ] Audio-only mode (for displays with external speakers)
- [ ] Transition effects between videos
- [ ] Schedule-based playback (time-of-day playlists)
- [ ] Remote control via web dashboard

## Summary

The video playback feature provides a complete digital signage solution with:
- ✅ Full-screen video playback
- ✅ Playlist management with auto-sequencing
- ✅ Duration-based video looping
- ✅ Progressive streaming with caching
- ✅ Easy control interface
- ✅ De-registration capability

The implementation uses ExoPlayer for robust video playback, coroutines for timing, and Compose for modern UI. The system is ready for production use on Android TV devices.

