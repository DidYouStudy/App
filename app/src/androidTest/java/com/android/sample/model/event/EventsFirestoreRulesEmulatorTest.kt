// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.event

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises server-side rules directly against isolated Auth and Firestore emulators. */
@RunWith(AndroidJUnit4::class)
class EventsFirestoreRulesEmulatorTest {
  private lateinit var app: FirebaseApp
  private lateinit var auth: FirebaseAuth
  private lateinit var db: FirebaseFirestore
  private lateinit var uid: String
  private val day = Instant.parse("2026-10-20T00:00:00Z")

  /** Creates an isolated emulator client and owner for each test. */
  @Before
  fun setup() = runBlocking {
    val name = "event-rules-${UUID.randomUUID()}"
    app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setProjectId("demo-didyoustudy")
                .setApplicationId("1:123:android:events")
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

  /** Releases only this test client's Firestore cache and authentication state. */
  @After
  fun cleanup() = runBlocking {
    if (::db.isInitialized) {
      db.terminate().await()
      db.clearPersistence().await()
    }
    if (::auth.isInitialized) auth.signOut()
    if (::app.isInitialized) app.delete()
  }

  /** Produces a valid event payload for direct SDK requests. */
  private fun eventData(): Map<String, Any?> =
      EventFirestoreMapper.encode(
          Event("a", uid, EventType.PROJECT, "Analysis", day, day.plusSeconds(3600), day)
      )

  /** Resolves an event document belonging to the test owner. */
  private fun event() = db.collection("users").document(uid).collection("events").document("a")

  /** Resolves the subject catalog document belonging to the test owner. */
  private fun catalog() =
      db.collection("users").document(uid).collection("meta").document("subjects")

  /** The owner can create, read, and delete events and read and write the catalog. */
  @Test
  fun ownerCanAccessValidData() = runBlocking {
    event().set(eventData()).await()
    catalog().set(mapOf("subjects" to emptyMap<String, Any>())).await()
    assertTrue(event().get(Source.SERVER).await().exists())
    assertTrue(catalog().get(Source.SERVER).await().exists())
    event().delete().await()
    assertFalse(event().get(Source.SERVER).await().exists())
  }

  /** The server rejects invalid top-level event fields and unexpected fields. */
  @Test
  fun rejectsInvalidEventData() = runBlocking {
    val data = eventData()
    listOf(
            data + ("ownerId" to "other"),
            data + ("type" to "INVALID"),
            data + ("subject" to " "),
            data + ("endsAt" to data["startsAt"]),
            data + ("endsAt" to Timestamp(day.plusSeconds(40 * 3600).epochSecond, 0)),
            data + ("tasks" to "bad"),
            data + ("unexpected" to true),
        )
        .forEach { invalid -> denied { event().set(invalid).await() } }
    denied { catalog().set(mapOf("subjects" to "bad")).await() }
  }

  /** Signed-out clients and other accounts cannot read or write the owner's data. */
  @Test
  fun deniesSignedOutAndOtherUsers() = runBlocking {
    val data = eventData()
    event().set(data).await()
    catalog().set(mapOf("subjects" to emptyMap<String, Any>())).await()
    auth.signOut()
    denied { event().get(Source.SERVER).await() }
    denied { event().set(data).await() }
    denied { catalog().get(Source.SERVER).await() }
    auth.signInAnonymously().await()
    denied { event().get(Source.SERVER).await() }
    denied { event().parent.get(Source.SERVER).await() }
    denied { event().set(data).await() }
    denied { event().delete().await() }
    denied { catalog().get(Source.SERVER).await() }
    denied { catalog().set(mapOf("subjects" to emptyMap<String, Any>())).await() }
  }

  /** Requires a server permission error rather than accepting an unrelated failure. */
  private suspend fun denied(action: suspend () -> Any?) {
    try {
      withTimeout(15000) { action() }
      fail("Expected server permission denial")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }
}
