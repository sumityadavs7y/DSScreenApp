package com.logicalvalley.digitalSignage.data.api

import android.util.Log
import com.google.gson.Gson
import com.logicalvalley.digitalSignage.config.AppConfig
import com.logicalvalley.digitalSignage.data.model.RegisterResponse
import com.logicalvalley.digitalSignage.util.SSLConfig
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

class SocketManager {
    private val TAG = "SocketManager"
    private var socket: Socket? = null
    private val gson = Gson()
    
    // State tracking for reconnection
    private var lastDeviceId: String? = null
    private var lastPlaylistId: String? = null

    // Create OkHttpClient with SSL configuration
    private val okHttpClient = SSLConfig.createOkHttpClient()

    fun connect(
        baseUrl: String = AppConfig.BASE_URL,
        onStatusChange: ((Boolean) -> Unit)? = null
    ) {
        if (socket?.connected() == true) return
        
        // Clean up any existing socket instance to prevent leaks
        socket?.disconnect()
        socket?.off()

        try {
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
                // Use custom OkHttpClient with SSL configuration
                callFactory = okHttpClient
                webSocketFactory = okHttpClient
            }
            socket = IO.socket(baseUrl, opts)
            
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "🔌 Socket Connected!")
                onStatusChange?.invoke(true)
                
                // Re-establish session state on reconnection
                val deviceId = lastDeviceId
                val playlistId = lastPlaylistId
                
                if (deviceId != null) {
                    if (playlistId != null) {
                        Log.d(TAG, "🔄 Auto-reconnecting player session: $deviceId")
                        val data = JSONObject().apply {
                            put("uid", deviceId)
                            put("playlistId", playlistId)
                        }
                        socket?.emit("device:player:connect", data)
                    } else {
                        Log.d(TAG, "🔄 Auto-rejoining device room: $deviceId")
                        socket?.emit("device:join", deviceId)
                    }
                }
            }
            
            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "🔌 Socket Disconnected")
                onStatusChange?.invoke(false)
            }
            
            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "❌ Socket Connection Error: ${args.getOrNull(0)}")
                onStatusChange?.invoke(false)
            }

            socket?.connect()
        } catch (e: URISyntaxException) {
            Log.e(TAG, "❌ Socket URI Syntax Error", e)
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }

    fun onRegistrationComplete(callback: (RegisterResponse) -> Unit) {
        socket?.on("registration:complete") { args ->
            val data = args.getOrNull(0) as? JSONObject
            if (data != null) {
                try {
                    val response = gson.fromJson(data.toString(), RegisterResponse::class.java)
                    callback(response)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to parse registration:complete data", e)
                }
            }
        }
    }

    fun onRemoteCommand(
        onFullscreenEnter: () -> Unit,
        onFullscreenExit: () -> Unit,
        onForceDeregister: () -> Unit
    ) {
        socket?.on("device:command:fullscreen-enter") {
            Log.d(TAG, "📺 Remote Command: Fullscreen Enter")
            onFullscreenEnter()
        }
        socket?.on("device:command:fullscreen-exit") {
            Log.d(TAG, "📺 Remote Command: Fullscreen Exit")
            onFullscreenExit()
        }
        socket?.on("device:force-deregister") {
            Log.w(TAG, "🚫 Remote Command: Force Deregister")
            onForceDeregister()
        }
    }

    fun joinDeviceRoom(deviceId: String) {
        lastDeviceId = deviceId
        lastPlaylistId = null // We are in registration mode
        Log.d(TAG, "🏠 Joining device room: $deviceId")
        socket?.emit("device:join", deviceId)
    }

    fun connectPlayer(uid: String, playlistId: String) {
        lastDeviceId = uid
        lastPlaylistId = playlistId
        Log.d(TAG, "🎮 Connecting player: $uid to playlist: $playlistId")
        val data = JSONObject().apply {
            put("uid", uid)
            put("playlistId", playlistId)
        }
        socket?.emit("device:player:connect", data)
    }

    fun sendPing(uid: String) {
        val data = JSONObject().apply {
            put("uid", uid)
        }
        socket?.emit("device:ping", data)
    }
}

