// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackedSubjectRulesEmulatorTest {

  private lateinit var app: FirebaseApp
  private lateinit var auth: FirebaseAuth
  private lateinit var db: FirebaseFirestore
  private lateinit var uid: String

  @Before
  fun setup() = runBlocking {
    val name = "tracked-rules-${UUID.randomUUID()}"
    app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setProjectId("demo-didyoustudy")
                .setApplicationId("1:123:android:trackedrules")
                .setApiKey("fake-${UUID.randomUUID()}")
                .build(),
            name,
        )
    auth = FirebaseAuth.getInstance(app)
    auth.useEmulator("10.0.2.2", 9099)
    db = FirebaseFirestore.getInstance(app)
    db.useEmulator("10.0.2.2", 8080)
    uid =
        auth
            .createUserWithEmailAndPassword("$name@example.test", "EmulatorOnly123!")
            .await()
            .user!!
            .uid
  }

  @After
  fun cleanup() = runBlocking {
    if (::db.isInitialized) {
      db.terminate().await()
      db.clearPersistence().await()
    }
    if (::auth.isInitialized) auth.signOut()
    if (::app.isInitialized) app.delete()
  }

  private fun doc() =
      db.collection("users").document(uid).collection("meta").document("trackedSubjects")

  private fun validPayload() = TrackedSubjectFirestoreMapper.encode(mapOf("Analysis I" to true))

  @Test
  fun ownerCanReadAndWriteValidData() = runBlocking {
    doc().set(validPayload()).await()
    assertTrue(doc().get(Source.SERVER).await().exists())
  }

  @Test
  fun rejectsInvalidDataSchema() = runBlocking {
    denied { doc().set(mapOf("subjects" to "not-a-map")).await() }
    denied { doc().set(mapOf("subjects" to emptyMap<String, Any>(), "extraKey" to true)).await() }
  }

  @Test
  fun deniesSignedOutAndOtherUsers() = runBlocking {
    doc().set(validPayload()).await()

    auth.signOut()
    denied { doc().get(Source.SERVER).await() }
    denied { doc().set(validPayload()).await() }

    auth.signInAnonymously().await()
    denied { doc().get(Source.SERVER).await() }
    denied { doc().set(validPayload()).await() }
  }

  private suspend fun denied(action: suspend () -> Any?) {
    try {
      withTimeout(15000) { action() }
      fail("Expected server permission denial")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }
}
