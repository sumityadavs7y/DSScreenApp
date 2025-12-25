package com.example.dsscreen.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton for Retrofit client
 */
object RetrofitInstance {
    // Base URL for the backend API
    // Use 10.0.2.2 for Android Emulator (maps to localhost)
    // For physical device, use your computer's IP address (e.g., "http://192.168.1.x:3000/")
    private const val BASE_URL = "http://10.0.2.2:3000/"

    /**
     * OkHttp client with logging interceptor
     */
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Retrofit instance
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Device API instance
     */
    val deviceApi: DeviceApi by lazy {
        retrofit.create(DeviceApi::class.java)
    }
}

