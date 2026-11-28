package com.example.data.firebase

import android.content.Context
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Provides the project's provisioned named Firestore database instance (`R.string.firestore_database_id`).
 * Used strictly for lightweight cross-device admin security state (active sessions, login alerts,
 * unauthorized attempts, 2FA config, admin allowlist) and on-demand visitor stats fallback.
 */
object FirestoreProvider {
    fun get(context: Context): FirebaseFirestore {
        val dbId = runCatching {
            context.applicationContext.getString(R.string.firestore_database_id).trim()
        }.getOrDefault("")
        return if (dbId.isNotEmpty()) {
            FirebaseFirestore.getInstance(dbId)
        } else {
            FirebaseFirestore.getInstance()
        }
    }
}
