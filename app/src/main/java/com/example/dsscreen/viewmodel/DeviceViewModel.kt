package com.example.dsscreen.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dsscreen.data.local.DeviceDataStore
import com.example.dsscreen.data.model.DeviceRegistrationResponse
import com.example.dsscreen.data.model.Playlist
import com.example.dsscreen.data.repository.DeviceRepository
import com.example.dsscreen.utils.DeviceUtils
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val repository = DeviceRepository()
    private val dataStore = DeviceDataStore(context)
    private val gson = Gson()

    // UI State
    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    // Playlist data after successful registration
    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
                            android.util.Log.d("DeviceViewModel", "Playlist loaded: ${storedPlaylist.name}, items: ${storedPlaylist.items?.size}")
                        } catch (e: Exception) {
                            // Failed to parse stored playlist, will need to re-register
                            android.util.Log.e("DeviceViewModel", "Failed to parse playlist", e)
                            e.printStackTrace()
                        }
                    } else {
                        android.util.Log.d("DeviceViewModel", "No playlist data found")
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.e("DeviceViewModel", "Timeout loading playlist")
            } catch (e: Exception) {
                android.util.Log.e("DeviceViewModel", "Error loading playlist", e)
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
            dataStore.clearRegistration()
            _registrationState.value = RegistrationState.Idle
            _playlist.value = null
            _isLoading.value = false
            android.util.Log.d("DeviceViewModel", "Registration cleared")
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

