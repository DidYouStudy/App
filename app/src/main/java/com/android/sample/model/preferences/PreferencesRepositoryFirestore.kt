// Co-authored-by: Gemini AI Agent
package com.android.sample.model.preferences

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class PreferencesRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : PreferencesRepository {

    private val collection = firestore.collection("preferences")

    override suspend fun getPreferences(userId: String): Result<UserPreferences?> = runCatching {
        val documentSnapshot = collection.document(userId).get().await()
        if (documentSnapshot.exists()) {
            documentSnapshot.toObject(UserPreferences::class.java)
        } else {
            null
        }
    }

    override suspend fun savePreferences(preferences: UserPreferences): Result<Unit> = runCatching {
        require(preferences.userId.isNotBlank()) { "User ID must not be blank" }
        collection.document(preferences.userId).set(preferences).await()
        Unit
    }
}