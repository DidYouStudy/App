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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Auth/Firestore SDK tests; use local emulators, never production credentials or data. */
@RunWith(AndroidJUnit4::class)
class EventsRepositoryFirestoreEmulatorTest {
  private lateinit var app: FirebaseApp
  private lateinit var db: FirebaseFirestore
  private lateinit var auth: FirebaseAuth
  private lateinit var repository: EventsRepositoryFirestore
  private lateinit var uid: String
  private val name = "events-${UUID.randomUUID()}"
  private val email = "$name@example.test"
  private val password = "EmulatorOnly123!"
  private val day = Instant.parse("2026-10-20T00:00:00Z")

  /** Creates an isolated SDK cache and authenticated emulator user for each test. */
  @Before
  fun setup() = runBlocking {
    initialize()
    uid = auth.createUserWithEmailAndPassword(email, password).await().user!!.uid
  }

  /** Configures emulator endpoints before either SDK performs any operation. */
  private fun initialize() {
    app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setProjectId("demo-didyoustudy")
                .setApplicationId("1:123:android:events")
                // Auth's emulator registry is keyed by API key and can retain deleted app
                // listeners.
                // A unique fake key isolates each client while the app name preserves disk cache.
                .setApiKey("fake-${UUID.randomUUID()}")
                .build(),
            name,
        )
    auth = FirebaseAuth.getInstance(app)
    auth.useEmulator("10.0.2.2", 9099)
    initializeFirestore()
  }

  /** Reopens only Firestore, keeping Auth and Firebase's heartbeat DataStore singleton alive. */
  private fun initializeFirestore() {
    db = FirebaseFirestore.getInstance(app)
    db.useEmulator("10.0.2.2", 8080)
    repository = EventsRepositoryFirestore(db, auth)
  }

  /** Releases listeners/cache files and removes only this test's local persistence. */
  @After
  fun cleanup() = runBlocking {
    if (::db.isInitialized) {
      db.terminate().await()
      db.clearPersistence().await()
    }
    if (::auth.isInitialized) auth.signOut()
    if (::app.isInitialized) app.delete()
  }

  /** Builds a complete event with exact microsecond precision for server round trips. */
  private fun event(id: String = "a", start: Long = 9, end: Long = 10) =
      Event(
          id,
          uid,
          EventType.PROJECT,
          "Analysis",
          day.plusSeconds(start * 3600),
          day.plusSeconds(end * 3600),
          day,
          location = "BC 410",
          description = "Details",
          tasks = listOf(EventTask("Exercise"), EventTask("Read", day.plusSeconds(1))),
      )

  /** Waits for server confirmation only in tests, never in the repository's write API. */
  private suspend fun sync() = withTimeout(15000) { db.waitForPendingWrites().await() }

  /** Resolves the tested user's document for direct rules and server assertions. */
  private fun document(id: String) =
      db.collection("users").document(uid).collection("events").document(id)

  /** CRUD, complete field mapping, stable retries and idempotent missing deletes. */
  @Test
  fun crudAndServerRoundTrip() = runBlocking {
    val original = event()
    repository.saveEvent(original).getOrThrow()
    sync()
    assertEquals(original, repository.getEvent(uid, "a").getOrThrow())
    assertEquals("PROJECT", document("a").get(Source.SERVER).await().getString("type"))
    val edited =
        original.copy(subject = "Physics", tasks = emptyList(), location = null, description = null)
    repeat(2) { repository.saveEvent(edited).getOrThrow() }
    sync()
    assertEquals(edited, repository.getEvent(uid, "a").getOrThrow())
    assertEquals(1, document("a").parent.get(Source.SERVER).await().size())
    repeat(2) { repository.deleteEvent(uid, "a").getOrThrow() }
    repository.deleteEvent(uid, "missing").getOrThrow()
    sync()
    assertEquals(
        EventsError.NOT_FOUND,
        (repository.getEvent(uid, "a").exceptionOrNull() as EventsException).error,
    )
  }

  /** Cross-midnight events overlap both days; boundary-only contact is excluded and ties sort. */
  @Test
  fun overlapOrderingAndLiveChanges() = runBlocking {
    val crossing = event("cross", -1, 1)
    val short = event("b", 9, 10)
    val tied = event("a", 9, 10)
    val long = event("long", 9, 11)
    listOf(event("ends", -2, 0), event("starts", 24, 25), long, short, tied, crossing).forEach {
      repository.saveEvent(it).getOrThrow()
    }
    sync()
    val flow = repository.observeEvents(uid, day, day.plusSeconds(86400))
    assertEquals(
        listOf(crossing, tied, short, long),
        withTimeout(15000) { flow.first { it.getOrThrow().size == 4 }.getOrThrow() },
    )
    val changed = async {
      withTimeout(15000) { flow.first { it.getOrThrow().size == 3 }.getOrThrow() }
    }
    repository.deleteEvent(uid, "b").getOrThrow()
    assertEquals(listOf(crossing, tied, long), changed.await())
  }

  /** Suggestions merge case/space variants and remain after deletes or subject removal. */
  @Test
  fun subjectCatalogAndOptionalFields() = runBlocking {
    repository.saveEvent(event().copy(subject = " analysis ")).getOrThrow()
    repository
        .saveEvent(event("b").copy(subject = "Physics", updatedAt = day.plusSeconds(1)))
        .getOrThrow()
    repository
        .saveEvent(event("c").copy(subject = "Analysis", updatedAt = day.plusSeconds(2)))
        .getOrThrow()
    repository.deleteEvent(uid, "c").getOrThrow()
    repository.saveEvent(event().copy(type = EventType.OTHER, subject = null)).getOrThrow()
    sync()
    assertEquals(
        listOf("Analysis", "Physics"),
        withTimeout(15000) { repository.observeSubjects(uid).first().getOrThrow() },
    )
    val optional =
        EventFirestoreMapper.encode(event("optional").copy(type = EventType.OTHER)).toMutableMap()
    listOf("subject", "location", "description", "tasks").forEach { optional.remove(it) }
    document("optional").set(optional).await()
    val read = repository.getEvent(uid, "optional").getOrThrow()
    assertNull(read.subject)
    assertTrue(read.tasks.isEmpty())
  }

  /** Offline create/edit/delete complete locally and synchronize without duplicate IDs. */
  @Test
  fun offlineMutationsAndReconnect() = runBlocking {
    repository.saveEvent(event("delete")).getOrThrow()
    sync()
    db.disableNetwork().await()
    val expected = event().copy(subject = "Physics")
    withTimeout(5000) {
      repository.saveEvent(event()).getOrThrow()
      repository.saveEvent(expected).getOrThrow()
      repository.saveEvent(expected).getOrThrow()
      repository.deleteEvent(uid, "delete").getOrThrow()
      repository.deleteEvent(uid, "missing").getOrThrow()
      assertEquals(expected, repository.getEvent(uid, "a").getOrThrow())
      assertEquals(
          listOf(expected),
          repository.observeEvents(uid, day, day.plusSeconds(86400)).first().getOrThrow(),
      )
      assertEquals(
          listOf("Analysis", "Physics"),
          repository.observeSubjects(uid).first().getOrThrow(),
      )
    }
    db.enableNetwork().await()
    sync()
    assertEquals(
        listOf("a"),
        document("a").parent.get(Source.SERVER).await().documents.map { it.id },
    )
  }

  /** Cached events and queued writes survive SDK recreation with the same persistent cache. */
  @Test
  fun offlineCacheSurvivesClientRestart() = runBlocking {
    repository.saveEvent(event("cached")).getOrThrow()
    sync()
    db.disableNetwork().await()
    repository.saveEvent(event("pending")).getOrThrow()
    db.terminate().await()
    initializeFirestore()
    db.disableNetwork().await()
    withTimeout(5000) {
      assertEquals(event("cached"), repository.getEvent(uid, "cached").getOrThrow())
      assertEquals(event("pending"), repository.getEvent(uid, "pending").getOrThrow())
    }
    db.enableNetwork().await()
    sync()
    assertEquals(2, document("cached").parent.get(Source.SERVER).await().size())
  }

  /** Rules reject malformed fields while the mapper rejects nested data rules cannot enumerate. */
  @Test
  fun rulesAndMalformedData() = runBlocking {
    val data = EventFirestoreMapper.encode(event())
    listOf(
            data + ("ownerId" to "other"),
            data + ("type" to "INVALID"),
            data + ("subject" to " "),
            data + ("endsAt" to data["startsAt"]),
            data + ("endsAt" to Timestamp(day.plusSeconds(40 * 3600).epochSecond, 0)),
            data + ("tasks" to "bad"),
            data + ("unexpected" to true),
        )
        .forEach { invalid -> denied { document("invalid").set(invalid).await() } }
    document("malformed").set(data + ("tasks" to listOf(mapOf("text" to "")))).await()
    assertEquals(
        EventsError.INVALID_EVENT_DATA,
        (repository.getEvent(uid, "malformed").exceptionOrNull() as EventsException).error,
    )
    assertEquals(
        EventsError.INVALID_EVENT_DATA,
        (withTimeout(15000) {
                  repository.observeEvents(uid, day, day.plusSeconds(86400)).first { it.isFailure }
                }
                .exceptionOrNull() as EventsException)
            .error,
    )
  }

  /** Direct SDK requests prove server rules, independently of repository owner checks. */
  @Test
  fun signedOutAndOtherUsersCannotAccessPrivateData() = runBlocking {
    repository.saveEvent(event()).getOrThrow()
    sync()
    val target = document("a")
    val catalog = db.collection("users").document(uid).collection("meta").document("subjects")
    auth.signOut()
    denied { target.get(Source.SERVER).await() }
    denied { target.set(EventFirestoreMapper.encode(event())).await() }
    denied { catalog.get(Source.SERVER).await() }
    auth.signInAnonymously().await()
    denied { target.get(Source.SERVER).await() }
    denied { target.parent.get(Source.SERVER).await() }
    denied { target.set(EventFirestoreMapper.encode(event())).await() }
    denied { target.delete().await() }
    denied { catalog.get(Source.SERVER).await() }
    denied { catalog.set(mapOf("subjects" to emptyMap<String, Any>())).await() }
    assertEquals(
        EventsError.PERMISSION_DENIED,
        (repository.getEvent(uid, "a").exceptionOrNull() as EventsException).error,
    )
  }

  /** Requires a concrete server permission error rather than any arbitrary failure. */
  private suspend fun denied(action: suspend () -> Any?) {
    try {
      withTimeout(15000) { action() }
      fail("Expected server permission denial")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }
}
