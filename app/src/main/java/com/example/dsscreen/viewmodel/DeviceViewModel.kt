package com.example.dsscreen.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dsscreen.data.local.DeviceDataStore
import com.example.dsscreen.data.model.DeviceRegistrationResponse
import com.example.dsscreen.data.model.Playlist
import com.example.dsscreen.data.repository.DeviceRepository
import com.example.dsscreen.utils.DeviceUtils
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for device registration
 */
class DeviceViewModel(
    private val context: Context
) : ViewModel() {
    private val TAG = "DeviceViewModel"
    private val repository = DeviceRepository()
    private val dataStore = DeviceDataStore(context)
    private val gson = Gson()
    
    // Update check interval (30 seconds)
    private val UPDATE_CHECK_INTERVAL = 30 * 1000L // 30 seconds in milliseconds

    // UI State
    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    // Playlist data after successful registration
    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Periodic update job
    private var updateCheckJob: Job? = null

    // Check if device is already registered
    val isRegistered = dataStore.isRegistered()
    val registrationData = dataStore.getRegistrationData()

    init {
        // Load stored playlist data on initialization
        viewModelScope.launch {
            try {
                // Add timeout to prevent infinite loading
                kotlinx.coroutines.withTimeout(5000L) { // 5 second timeout
                    val playlistJson = dataStore.getPlaylistData().first()
                    if (playlistJson != null && playlistJson.isNotBlank()) {
                        try {
                            val storedPlaylist = gson.fromJson(playlistJson, Playlist::class.java)
                            _playlist.value = storedPlaylist
                            Log.d(TAG, "Playlist loaded: ${storedPlaylist.name}, items: ${storedPlaylist.items?.size}")
                            
                            // Start periodic update check if registered
                            startPeriodicUpdateCheck()
                        } catch (e: Exception) {
                            // Failed to parse stored playlist, will need to re-register
                            Log.e(TAG, "Failed to parse playlist", e)
                            e.printStackTrace()
                        }
                    } else {
                        Log.d(TAG, "No playlist data found")
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e(TAG, "Timeout loading playlist")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading playlist", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Register device with playlist code
     */
    fun registerDevice(playlistCode: String) {
        if (playlistCode.length != 5) {
            _registrationState.value = RegistrationState.Error("Playlist code must be exactly 5 characters")
            return
        }

        viewModelScope.launch {
            _registrationState.value = RegistrationState.Loading

            try {
                // Generate device UID and get device info
                val deviceUid = DeviceUtils.generateDeviceUID(context)
                val deviceInfo = DeviceUtils.getDeviceInfo(context)

                // Register device
                val result = repository.registerDevice(playlistCode, deviceUid, deviceInfo)

                result.fold(
                    onSuccess = { response ->
                        // Save registration data
                        val timestamp = SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss'Z'",
                            Locale.US
                        ).format(Date())

                        // Serialize playlist to JSON
                        val playlistJson = gson.toJson(response.playlist)

                        dataStore.saveRegistration(
                            deviceUid = deviceUid,
                            playlistCode = playlistCode,
                            deviceId = response.device.id,
                            playlistId = response.playlist.id,
                            playlistName = response.playlist.name,
                            registeredAt = timestamp,
                            playlistData = playlistJson
                        )

                        _playlist.value = response.playlist
                        _registrationState.value = RegistrationState.Success(response)
                        
                        // Start periodic update check
                        startPeriodicUpdateCheck()
                    },
                    onFailure = { error ->
                        _registrationState.value = RegistrationState.Error(
                            error.message ?: "Registration failed"
                        )
                    }
                )
            } catch (e: Exception) {
                _registrationState.value = RegistrationState.Error(
                    e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    /**
     * Clear registration and allow re-registration
     */
    fun clearRegistration() {
        viewModelScope.launch {
            // Stop periodic update check
            stopPeriodicUpdateCheck()
            
            dataStore.clearRegistration()
            _registrationState.value = RegistrationState.Idle
            _playlist.value = null
            _isLoading.value = false
            Log.d(TAG, "Registration cleared")
        }
    }
    
    /**
     * Force reload playlist from storage
     */
    fun reloadPlaylist() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val playlistJson = dataStore.getPlaylistData().first()
                if (playlistJson != null && playlistJson.isNotBlank()) {
                    val storedPlaylist = gson.fromJson(playlistJson, Playlist::class.java)
                    _playlist.value = storedPlaylist
                    android.util.Log.d("DeviceViewModel", "Playlist reloaded: ${storedPlaylist.name}")
                } else {
                    android.util.Log.d("DeviceViewModel", "No playlist to reload")
                }
            } catch (e: Exception) {
                android.util.Log.e("DeviceViewModel", "Failed to reload playlist", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Reset registration state to idle
     */
    fun resetState() {
        _registrationState.value = RegistrationState.Idle
    }
    
    /**
     * Start periodic check for playlist updates
     */
    fun startPeriodicUpdateCheck() {
        // Cancel existing job if any
        updateCheckJob?.cancel()
        
        updateCheckJob = viewModelScope.launch {
            while (isActive) {
                delay(UPDATE_CHECK_INTERVAL)
                checkForPlaylistUpdates()
            }
        }
        
        Log.d(TAG, "Started periodic update check (every ${UPDATE_CHECK_INTERVAL / 1000 / 60} minutes)")
    }
    
    /**
     * Stop periodic update check
     */
    fun stopPeriodicUpdateCheck() {
        updateCheckJob?.cancel()
        updateCheckJob = null
        Log.d(TAG, "Stopped periodic update check")
    }
    
    /**
     * Check for playlist updates from server
     * Silently updates playlist if changes detected
     * Does nothing if server is unreachable or no changes
     */
    private suspend fun checkForPlaylistUpdates() {
        try {
            val registrationData = dataStore.getRegistrationData().first()
            val playlistCode = registrationData["playlistCode"] ?: run {
                Log.w(TAG, "No playlist code found, skipping update check")
                return
            }
            
            val deviceUid = DeviceUtils.generateDeviceUID(context)
            val deviceInfo = DeviceUtils.getDeviceInfo(context)
            
            Log.d(TAG, "🔄 Checking for playlist updates...")
            
            // Re-register to get latest playlist (this is a safe operation)
            val result = repository.registerDevice(playlistCode, deviceUid, deviceInfo)
            
            result.fold(
                onSuccess = { response ->
                    val newPlaylist = response.playlist
                    val currentPlaylist = _playlist.value
                    
                    // Check if playlist has changed
                    if (hasPlaylistChanged(currentPlaylist, newPlaylist)) {
                        Log.d(TAG, "✓ Playlist updated! New: ${newPlaylist.name}, Items: ${newPlaylist.items?.size}")
                        
                        // Update stored playlist
                        val playlistJson = gson.toJson(newPlaylist)
                        
                        dataStore.saveRegistration(
                            deviceUid = deviceUid,
                            playlistCode = playlistCode,
                            deviceId = response.device.id,
                            playlistId = newPlaylist.id,
                            playlistName = newPlaylist.name,
                            registeredAt = SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                                Locale.US
                            ).format(Date()),
                            playlistData = playlistJson
                        )
                        
                        // Update playlist in memory
                        _playlist.value = newPlaylist
                        
                        Log.d(TAG, "✅ Playlist synchronized successfully")
                    } else {
                        Log.d(TAG, "○ No changes detected")
                    }
                },
                onFailure = { error ->
                    // Server unreachable or error - continue with cached playlist
                    Log.w(TAG, "⚠ Unable to check updates (server unreachable): ${error.message}")
                    Log.d(TAG, "→ Continuing with cached playlist")
                }
            )
        } catch (e: Exception) {
            // Any error - continue with cached playlist
            Log.w(TAG, "⚠ Update check failed: ${e.message}")
            Log.d(TAG, "→ Continuing with cached playlist")
        }
    }
    
    /**
     * Check if playlist has changed
     */
    private fun hasPlaylistChanged(current: Playlist?, new: Playlist): Boolean {
        if (current == null) return true
        
        // Compare basic properties
        if (current.id != new.id) return true
        if (current.name != new.name) return true
        if (current.updatedAt != new.updatedAt) return true
        
        // Compare items count
        val currentItemsSize = current.items?.size ?: 0
        val newItemsSize = new.items?.size ?: 0
        if (currentItemsSize != newItemsSize) return true
        
        // Compare each item
        val currentItems = current.items?.sortedBy { it.order } ?: emptyList()
        val newItems = new.items?.sortedBy { it.order } ?: emptyList()
        
        currentItems.forEachIndexed { index, currentItem ->
            val newItem = newItems.getOrNull(index) ?: return true
            
            if (currentItem.id != newItem.id) return true
            if (currentItem.videoId != newItem.videoId) return true
            if (currentItem.duration != newItem.duration) return true
            if (currentItem.order != newItem.order) return true
        }
        
        return false
    }
    
    override fun onCleared() {
        super.onCleared()
        stopPeriodicUpdateCheck()
    }
}

/**
 * Registration state sealed class
 */
sealed class RegistrationState {
    object Idle : RegistrationState()
    object Loading : RegistrationState()
    data class Success(val response: DeviceRegistrationResponse) : RegistrationState()
    data class Error(val message: String) : RegistrationState()
}

