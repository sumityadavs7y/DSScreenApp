# Offline Playback Guide - DSScreen Android App

## How It Works

The DSScreen app automatically caches videos as they play, enabling **full offline functionality** after the initial viewing.

### Automatic Caching

1. **First Playback** (Online Required)
   - Videos stream from server
   - ExoPlayer automatically caches data as it streams
   - No manual download needed

2. **Subsequent Playback** (Offline Capable)
   - Videos play instantly from cache
   - No internet required
   - Smooth, buffer-free playback

3. **Cache Management**
   - **Size Limit**: 1 GB
   - **Eviction**: LRU (Least Recently Used)
   - **Location**: `/data/data/com.example.dsscreen/cache/video_cache/`
   - **Persistence**: Survives app restarts

### Testing Offline Mode

**Setup:**
```
1. Register device with playlist code
2. Let all videos play through once (online)
3. Videos are now cached
4. Turn off internet/server
5. Reopen app → Videos play from cache
```

**Verify Cache:**
```
Check Logcat for:
"Cached videos: X, Size: YMB"
"Video cached check for [url]: true"
```

### Cache Storage

Videos are stored in device internal storage:
```
/data/data/com.example.dsscreen/cache/video_cache/
├── [video_id_1].v3.exo
├── [video_id_2].v3.exo
└── ...
```

### Configuration

**Adjust cache size in `VideoCacheManager.kt`:**
```kotlin
// Increase to 2 GB
private const val MAX_CACHE_SIZE = 2048L * 1024 * 1024

// Decrease to 500 MB
private const val MAX_CACHE_SIZE = 500L * 1024 * 1024
```

### Troubleshooting

**Videos not caching:**
1. Check available device storage
2. Review Logcat for "VideoCacheManager" logs
3. Ensure videos play completely at least once
4. Verify cache directory exists

**Offline playback fails:**
1. Ensure videos were fully cached
2. Check that playlist data is stored (DataStore)
3. Verify cache wasn't cleared
4. Review Logcat for errors

**Cache fills up:**
- Oldest videos automatically removed (LRU)
- Increase `MAX_CACHE_SIZE` if needed
- Currently playing videos never evicted

### Benefits

✅ Works completely offline after initial setup  
✅ Instant video playback (no buffering)  
✅ Automatic cache management  
✅ No manual downloads required  
✅ Survives app restarts  
✅ Perfect for unreliable networks  

### Storage Requirements

**Example:**
- 5 videos × 100MB each = 500MB
- Fits within default 1GB cache
- Automatic cleanup when full

The app is now fully offline-capable with automatic video caching!

