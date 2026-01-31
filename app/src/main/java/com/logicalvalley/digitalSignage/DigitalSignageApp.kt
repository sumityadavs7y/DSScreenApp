package com.logicalvalley.digitalSignage

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.logicalvalley.digitalSignage.util.SSLConfig

class DigitalSignageApp : Application(), ImageLoaderFactory {
    
    override fun onCreate() {
        super.onCreate()
    }

    override fun newImageLoader(): ImageLoader {
        // Create ImageLoader with custom OkHttpClient that has SSL configuration
        return ImageLoader.Builder(this)
            .okHttpClient(SSLConfig.createOkHttpClient())
            .crossfade(true)
            .build()
    }
}

