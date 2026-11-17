package com.example.data.firebase

import android.content.Context
import com.example.DelivioApplication
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseClientProvider {
    fun getFirestore(context: Context): FirebaseFirestore {
        val appCtx = context.applicationContext ?: context
        DelivioApplication.ensureFirebase(appCtx)
        val databaseId = try {
            appCtx.getString(R.string.firestore_database_id).trim()
        } catch (_: Throwable) {
            ""
        }
        return if (databaseId.isEmpty() || databaseId == "(default)") {
            FirebaseFirestore.getInstance()
        } else {
            FirebaseFirestore.getInstance(databaseId)
        }
    }

    fun getAuth(context: Context): FirebaseAuth {
        val appCtx = context.applicationContext ?: context
        DelivioApplication.ensureFirebase(appCtx)
        return FirebaseAuth.getInstance()
    }
}
