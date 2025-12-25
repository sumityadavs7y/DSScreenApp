# Device Registration Feature - DSScreen Android App

## Overview

The DSScreen Android app now includes device registration functionality that allows Android TV devices to register to playlists using a 5-character playlist code, similar to the web implementation.

## Features

✅ **Device Registration with Playlist Code**
- Enter 5-character alphanumeric playlist code
- Auto-focus and navigation between input boxes
- Automatic device fingerprinting using Android ID

✅ **Persistent Storage**
- Registration data stored using DataStore
- Device remains registered across app restarts
- Option to re-register if needed

✅ **Device Information Collection**
- Screen resolution
- Device model and manufacturer
- Android version
- Unique device UID

✅ **Playlist Display**
- View registered playlist details
- See all playlist items with video information
- Display video order, duration, and resolution

## Architecture

### Data Layer
- **Models**: `Device.kt`, `Playlist.kt`, `ApiResponse.kt`
- **API**: `DeviceApi.kt` - Retrofit interface for device registration
- **Repository**: `DeviceRepository.kt` - Handles API calls and data operations
- **Local Storage**: `DeviceDataStore.kt` - Manages persistent registration data

### Domain Layer
- **ViewModel**: `DeviceViewModel.kt` - Manages UI state and business logic
- **Utils**: `DeviceUtils.kt` - Device fingerprinting and info collection

### Presentation Layer
- **Screens**: 
  - `DeviceRegistrationScreen.kt` - 5-character code input UI
  - `PlayerScreen.kt` - Shows registered device and playlist info
- **Navigation**: `NavGraph.kt` - Handles screen transitions

## API Endpoint

### Register Device
```http
POST /playlists/device/register
Content-Type: application/json

{
  "playlistCode": "ABCD1",
  "uid": "AND-A7F2E1B9C4D3",
  "deviceInfo": {
    "resolution": "1920x1080",
    "deviceModel": "Android TV",
    "androidVersion": "Android 13 (API 33)",
    "manufacturer": "Google",
    "brand": "Android",
    "timestamp": "2025-12-25T10:00:00Z",
    "location": "Android TV"
  }
}
```

**Response:**
```json
{
  "success": true,
  "message": "Device registered successfully",
  "data": {
    "device": {
      "id": "uuid",
      "uid": "AND-A7F2E1B9C4D3",
      "lastSeen": "2025-12-25T10:00:00Z"
    },
    "playlist": {
      "id": "uuid",
      "name": "My Playlist",
      "code": "ABCD1",
      "items": [
        {
          "id": "uuid",
          "order": 0,
          "duration": 30,
          "video": {
            "id": "uuid",
            "fileName": "video1.mp4",
            "resolution": "1920x1080"
          }
        }
      ]
    }
  }
}
```

## Configuration

### Base URL
The app is configured to connect to the backend at `http://10.0.2.2:3000/` (Android emulator localhost).

**For physical devices**, update the base URL in `RetrofitInstance.kt`:
```kotlin
private const val BASE_URL = "http://YOUR_COMPUTER_IP:3000/"
// Example: "http://192.168.1.100:3000/"
```

## How to Use

### 1. **Start the Backend**
```bash
cd dsScreenBackend
npm start
```

### 2. **Create a Playlist in the Web Dashboard**
- Login to the web dashboard
- Create a playlist and add videos
- Note the 5-character playlist code (e.g., "ABCD1")

### 3. **Run the Android App**
- Open the DSScreen project in Android Studio
- Run on Android TV emulator or physical device
- The app will show the Device Registration screen

### 4. **Register Device**
- Enter the 5-character playlist code
- Code input supports:
  - Auto-focus to next box when character entered
  - Backspace navigation to previous box
  - Paste support for all 5 characters
- Click "Register Device"

### 5. **View Registered Device**
- After successful registration, see:
  - Playlist name and code
  - Device UID and ID
  - Registration timestamp
  - List of videos in the playlist
- Option to re-register if needed

## Device UID Generation

The app generates a unique device identifier using:
- Android Secure ID (survives factory reset on Android 8+)
- Device manufacturer, model, and brand
- SHA-256 hash for uniqueness
- Format: `AND-{12-char-hash}` (e.g., `AND-A7F2E1B9C4D3`)

## Dependencies

Added to `libs.versions.toml` and `build.gradle.kts`:
- **Retrofit** 2.9.0 - HTTP client
- **OkHttp** 4.12.0 - Logging and networking
- **Gson** 2.10.1 - JSON parsing
- **Navigation Compose** 2.7.7 - Screen navigation
- **ViewModel** 2.8.7 - UI state management
- **DataStore** 1.1.1 - Persistent storage
- **Coroutines** 1.7.3 - Async operations
- **Material 3** 1.3.1 - UI components

## Testing

### Using Android Emulator
1. Make sure backend is running on `localhost:3000`
2. App uses `10.0.2.2:3000` to access host machine
3. Enter playlist code and test registration

### Using Physical Android TV Device
1. Update `BASE_URL` in `RetrofitInstance.kt` with your computer's IP
2. Ensure device and computer are on same network
3. Make sure backend allows connections from network
4. Test registration

## Troubleshooting

### "Playlist not found or inactive"
- Verify the playlist code is correct (case-sensitive)
- Check that the playlist is active in the database
- Ensure backend is running

### "Network error"
- Check internet permission in `AndroidManifest.xml`
- Verify backend URL is correct
- For physical devices, use computer's IP instead of 10.0.2.2
- Check firewall settings

### "Registration failed"
- Check backend logs for errors
- Verify request format matches API expectations
- Check OkHttp logs in Logcat for API response

## Future Enhancements

Potential features to add:
- [ ] Video playback from registered playlist
- [ ] Automatic playlist refresh/sync
- [ ] Offline mode with cached videos
- [ ] Schedule support for time-based playback
- [ ] Device name customization
- [ ] Multi-playlist support
- [ ] Device status reporting to backend

## Files Created

```
DSScreen/app/src/main/java/com/example/dsscreen/
├── data/
│   ├── api/
│   │   ├── DeviceApi.kt
│   │   └── RetrofitInstance.kt
│   ├── local/
│   │   └── DeviceDataStore.kt
│   ├── model/
│   │   ├── ApiResponse.kt
│   │   ├── Device.kt
│   │   └── Playlist.kt
│   └── repository/
│       └── DeviceRepository.kt
├── navigation/
│   └── NavGraph.kt
├── ui/
│   └── screens/
│       ├── DeviceRegistrationScreen.kt
│       └── PlayerScreen.kt
├── utils/
│   └── DeviceUtils.kt
├── viewmodel/
│   └── DeviceViewModel.kt
└── MainActivity.kt (updated)
```

## Summary

The device registration feature is now fully implemented and matches the web version's functionality. Devices can register using playlist codes, store registration data persistently, and view playlist information. The implementation follows Android best practices with MVVM architecture, Compose UI, and proper dependency injection.

