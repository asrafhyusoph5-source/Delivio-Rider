package com.example

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class DelivioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureFirebase(this)
    }

    companion object {
        fun ensureFirebase(context: Context) {
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    val app = try {
                        FirebaseApp.initializeApp(context)
                    } catch (_: Throwable) {
                        null
                    }
                    if (app == null || FirebaseApp.getApps(context).isEmpty()) {
                        val options = FirebaseOptions.Builder()
                            .setApplicationId("com.deliviorider.app")
                            .setProjectId("delivio-driver")
                            .setApiKey("AIzaSyFallbackKeyForDelivioDriverApp")
                            .build()
                        FirebaseApp.initializeApp(context.applicationContext, options)
                    }
                }
            } catch (_: Throwable) {
                // Safety guard to never crash application startup
            }
        }
    }
}
