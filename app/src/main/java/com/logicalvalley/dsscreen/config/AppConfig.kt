package com.logicalvalley.dsscreen.config

/**
 * Application-wide configuration constants
 * 
 * This centralized configuration file makes it easy to manage
 * environment-specific settings like API endpoints.
 */
object AppConfig {
    /**
     * Base URL for the backend API
     * 
     * IMPORTANT: Change this based on your environment:
     * - Android Emulator: "http://10.0.2.2:3000/"
     * - Physical Device: "http://YOUR_COMPUTER_IP:3000/" (e.g., "http://192.168.1.100:3000/")
     * - Production: "https://your-production-domain.com/"
     */
    const val BASE_URL = "https://signage.logicalvalley.in/"
    
    /**
     * Get the base URL without trailing slash
     * Used for constructing video URLs
     */
    val BASE_URL_WITHOUT_SLASH: String
        get() = BASE_URL.trimEnd('/')
}

