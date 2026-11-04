// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.groupsession

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the repository with separate signed-in clients against both Firebase emulators. */
@RunWith(AndroidJUnit4::class)
class GroupSessionsRepositoryFirestoreEmulatorTest {
  private data class Client(
      val app: FirebaseApp,
      val auth: FirebaseAuth,
      val db: FirebaseFirestore,
      val uid: String,
      val repository: GroupSessionsRepositoryFirestore,
  )

  private val clients = mutableListOf<Client>()
  private lateinit var creator: Client
  private lateinit var member: Client
  private lateinit var outsider: Client

  /** Creates independent Auth and Firestore emulator clients for the session participants. */
  @Before
  fun setUp() = runBlocking {
    clients.clear()
    creator = newClient("creator")
    member = newClient("member")
    outsider = newClient("outsider")
  }

  /** Releases every isolated Firebase client and its local Firestore cache. */
  @After
  fun tearDown() = runBlocking {
    clients.forEach { client ->
      timed {
        client.db.terminate().await()
        client.db.clearPersistence().await()
      }
      client.auth.signOut()
      client.app.delete()
    }
    clients.clear()
  }

  /** A creator's saved session round-trips identically for another signed-in user. */
  @Test
  fun creatorSavesAndAnotherUserReadsEqualSession() = runBlocking {
    val value = session(creator, uniqueId())

    creator.repository.saveSession(creator.uid, value).getOrThrow()
    sync(creator)

    assertEquals(value, outsider.repository.getSession(value.id).getOrThrow())
  }

  /** Creation rejects mismatched creator/auth identities and membership beyond the creator. */
  @Test
  fun createRejectsMismatchedIdentityAndAdditionalMembers() = runBlocking {
    val wrongCreatorId = uniqueId()
    val wrongCreator = session(member, wrongCreatorId)
    assertError(
        creator.repository.saveSession(creator.uid, wrongCreator),
        EventsError.PERMISSION_DENIED,
    )
    assertMissing(outsider, wrongCreatorId)

    val wrongAuthId = uniqueId()
    val wrongAuth = session(member, wrongAuthId)
    assertError(
        creator.repository.saveSession(member.uid, wrongAuth),
        EventsError.PERMISSION_DENIED,
    )
    assertMissing(outsider, wrongAuthId)

    val extraMemberId = uniqueId()
    val extraMember =
        session(creator, extraMemberId).copy(memberIds = listOf(creator.uid, member.uid))
    assertError(
        creator.repository.saveSession(creator.uid, extraMember),
        EventsError.PERMISSION_DENIED,
    )
    assertMissing(outsider, extraMemberId)
  }

  /** Creator edits retain stored membership and creation time after a member joins. */
  @Test
  fun creatorEditKeepsStoredMembersAndCreatedAt() = runBlocking {
    val original = session(creator, uniqueId())
    creator.repository.saveSession(creator.uid, original).getOrThrow()
    member.repository.joinSession(member.uid, original.id).getOrThrow()
    sync(member)

    val edited =
        original.copy(
            memberIds = listOf(creator.uid),
            createdAt = original.createdAt.plusSeconds(30),
            updatedAt = now().plusSeconds(30),
            subject = "Physics",
            location = "Library",
        )
    creator.repository.saveSession(creator.uid, edited).getOrThrow()
    sync(creator)

    assertEquals(
        edited.copy(
            memberIds = listOf(creator.uid, member.uid),
            createdAt = original.createdAt,
        ),
        outsider.repository.getSession(original.id).getOrThrow(),
    )
  }

  /** Clearing optional details through a save persists no location or description value. */
  @Test
  fun saveClearsOptionalLocationAndDescription() = runBlocking {
    val original = session(creator, uniqueId()).copy(location = "Library", description = "Review")
    creator.repository.saveSession(creator.uid, original).getOrThrow()
    val cleared =
        original.copy(location = null, description = null, updatedAt = now().plusSeconds(1))

    creator.repository.saveSession(creator.uid, cleared).getOrThrow()
    sync(creator)

    val data = serverData(outsider, original.id)
    assertNull(data["location"])
    assertNull(data["description"])
    assertEquals(cleared, outsider.repository.getSession(original.id).getOrThrow())
  }

  /** Non-creators and attempts to change creator identity fail without modifying stored data. */
  @Test
  fun unauthorizedSavesLeaveSessionUnchanged() = runBlocking {
    val created = session(creator, uniqueId())
    creator.repository.saveSession(creator.uid, created).getOrThrow()
    member.repository.joinSession(member.uid, created.id).getOrThrow()
    sync(member)
    val original = created.copy(memberIds = listOf(creator.uid, member.uid))
    val before = serverData(outsider, original.id)

    assertError(
        member.repository.saveSession(member.uid, original.copy(subject = "Physics")),
        EventsError.PERMISSION_DENIED,
    )
    assertEquals(before, serverData(outsider, original.id))

    val changedCreator = original.copy(createdBy = member.uid)
    assertError(
        creator.repository.saveSession(creator.uid, changedCreator),
        EventsError.PERMISSION_DENIED,
    )
    assertEquals(before, serverData(outsider, original.id))
  }

  /** Missing lookups return NOT_FOUND. */
  @Test
  fun missingSessionIsNotFound() = runBlocking {
    assertError(outsider.repository.getSession(uniqueId()), EventsError.NOT_FOUND)
  }

  /** Public observations filter end boundaries and sort by start, end, then document ID. */
  @Test
  fun observeSessionsFiltersAndSortsForAnotherUser() = runBlocking {
    val base = now()
    val threshold = base.plusSeconds(1800)
    val sameTimeZ = session(creator, "z", base.plusSeconds(3600), base.plusSeconds(7200))
    val sameTimeA = session(creator, "a", base.plusSeconds(3600), base.plusSeconds(7200))
    val longer = session(creator, "longer", base.plusSeconds(3600), base.plusSeconds(10800))
    val later = session(creator, "later", base.plusSeconds(7200), base.plusSeconds(10800))
    val endingAt = session(creator, "ending-at", threshold.minusSeconds(3600), threshold)
    val endingBefore =
        session(creator, "ending-before", threshold.minusSeconds(3600), threshold.minusSeconds(1))
    listOf(later, endingAt, sameTimeZ, endingBefore, longer, sameTimeA).forEach {
      creator.repository.saveSession(creator.uid, it).getOrThrow()
    }
    sync(creator)

    assertEquals(
        listOf(sameTimeA, sameTimeZ, longer, later),
        withTimeout(TIMEOUT_MILLIS) {
          outsider.repository.observeSessions(threshold).first().getOrThrow()
        },
    )
  }

  /** Membership and creator edits arrive in another user's live observation flow. */
  @Test
  fun observesJoinLeaveAndEditFromAnotherUser() = runBlocking {
    val created = session(creator, uniqueId())
    creator.repository.saveSession(creator.uid, created).getOrThrow()
    member.repository.joinSession(member.uid, created.id).getOrThrow()
    sync(member)
    val original = created.copy(memberIds = listOf(creator.uid, member.uid))
    val flow = outsider.repository.observeSessions(now())

    assertEquals(
        original,
        withTimeout(TIMEOUT_MILLIS) { flow.first { it.getOrThrow().isNotEmpty() }.getOrThrow()[0] },
    )

    val joined =
        async(Dispatchers.IO) {
          withTimeout(TIMEOUT_MILLIS) {
            flow.first { result -> result.getOrThrow().single().memberIds.contains(outsider.uid) }
          }
        }
    outsider.repository.joinSession(outsider.uid, original.id).getOrThrow()
    sync(outsider)
    assertTrue(joined.await().isSuccess)

    val left =
        async(Dispatchers.IO) {
          withTimeout(TIMEOUT_MILLIS) {
            flow.first { result -> !result.getOrThrow().single().memberIds.contains(member.uid) }
          }
        }
    member.repository.leaveSession(member.uid, original.id).getOrThrow()
    sync(member)
    assertTrue(left.await().isSuccess)

    val edited = original.copy(subject = "Updated", updatedAt = now().plusSeconds(10))
    val edit =
        async(Dispatchers.IO) {
          withTimeout(TIMEOUT_MILLIS) {
            flow.first { result -> result.getOrThrow().single().subject == edited.subject }
          }
        }
    creator.repository.saveSession(creator.uid, edited).getOrThrow()
    sync(creator)
    assertTrue(edit.await().isSuccess)
  }

  /** Signed-out observation emits a permission failure. */
  @Test
  fun signedOutObservationFailsWithPermissionDenied() = runBlocking {
    member.auth.signOut()

    val result = withTimeout(TIMEOUT_MILLIS) { member.repository.observeSessions(now()).first() }

    assertError(result, EventsError.PERMISSION_DENIED)
  }

  /** Repeated joins and non-member leaves are idempotent and preserve updatedAt. */
  @Test
  fun joinAndLeaveAreIdempotentAndDoNotChangeUpdatedAt() = runBlocking {
    val created = session(creator, uniqueId())
    creator.repository.saveSession(creator.uid, created).getOrThrow()
    member.repository.joinSession(member.uid, created.id).getOrThrow()
    sync(member)
    val original = created.copy(memberIds = listOf(creator.uid, member.uid))

    member.repository.joinSession(member.uid, original.id).getOrThrow()
    member.repository.joinSession(member.uid, original.id).getOrThrow()
    outsider.repository.leaveSession(outsider.uid, original.id).getOrThrow()
    sync(member)

    val afterNoOps = serverData(outsider, original.id)
    assertEquals(listOf(creator.uid, member.uid), afterNoOps["memberIds"])
    assertEquals(timestamp(original.updatedAt), afterNoOps["updatedAt"])
    assertEquals(
        original.updatedAt,
        outsider.repository.getSession(original.id).getOrThrow().updatedAt,
    )

    member.repository.leaveSession(member.uid, original.id).getOrThrow()
    sync(member)
    val afterLeave = serverData(outsider, original.id)
    assertEquals(listOf(creator.uid), afterLeave["memberIds"])
    assertEquals(timestamp(original.updatedAt), afterLeave["updatedAt"])
  }

  /** The creator cannot leave and missing-session joins or leaves report NOT_FOUND. */
  @Test
  fun creatorLeaveIsInvalidAndMissingMembershipChangesAreNotFound() = runBlocking {
    val original = session(creator, uniqueId())
    creator.repository.saveSession(creator.uid, original).getOrThrow()
    sync(creator)

    assertError(
        creator.repository.leaveSession(creator.uid, original.id),
        EventsError.INVALID_ARGUMENT,
    )
    assertTrue(
        outsider.repository.getSession(original.id).getOrThrow().memberIds.contains(creator.uid)
    )

    val missing = uniqueId()
    assertError(member.repository.joinSession(member.uid, missing), EventsError.NOT_FOUND)
    assertError(member.repository.leaveSession(member.uid, missing), EventsError.NOT_FOUND)
  }

  /** A user cannot join an ended session but an existing member can still leave it. */
  @Test
  fun endedSessionRejectsJoinAndAllowsLeave() = runBlocking {
    val original = session(creator, uniqueId())
    creator.repository.saveSession(creator.uid, original).getOrThrow()
    member.repository.joinSession(member.uid, original.id).getOrThrow()
    sync(member)
    val endedAt = now().minusSeconds(60)
    val ended =
        original.copy(
            memberIds = listOf(creator.uid, member.uid),
            startsAt = endedAt.minusSeconds(3600),
            endsAt = endedAt,
            updatedAt = now(),
        )
    creator.repository.saveSession(creator.uid, ended).getOrThrow()
    sync(creator)

    assertError(
        outsider.repository.joinSession(outsider.uid, original.id),
        EventsError.PERMISSION_DENIED,
    )
    member.repository.leaveSession(member.uid, original.id).getOrThrow()
    sync(member)
    assertEquals(
        listOf(creator.uid),
        outsider.repository.getSession(original.id).getOrThrow().memberIds,
    )
  }

  /** A thirtieth member can join, while a thirty-first is denied without changing membership. */
  @Test
  fun joiningFullSessionIsDeniedAndLeavesMembersUnchanged() = runBlocking {
    val original = session(creator, uniqueId())
    creator.repository.saveSession(creator.uid, original).getOrThrow()
    val existingMembers = mutableListOf(creator)
    existingMembers += member
    existingMembers += outsider
    repeat(GroupSession.MAX_MEMBERS - existingMembers.size) { index ->
      existingMembers += newClient("filler-$index")
    }
    existingMembers.forEach { client ->
      client.repository.joinSession(client.uid, original.id).getOrThrow()
      sync(client)
    }
    sync(creator)
    val before = serverData(outsider, original.id)["memberIds"]
    assertEquals(GroupSession.MAX_MEMBERS, (before as List<*>).size)

    val finalUser = newClient("full-session-extra")
    assertError(
        finalUser.repository.joinSession(finalUser.uid, original.id),
        EventsError.PERMISSION_DENIED,
    )
    sync(finalUser)
    assertEquals(before, serverData(outsider, original.id)["memberIds"])
  }

  /** Concurrent joins and a simultaneous join/leave retain both intended membership changes. */
  @Test
  fun concurrentMembershipChangesAreNotLostAcrossSeveralSessions() = runBlocking {
    repeat(3) {
      val joinId = uniqueId()
      creator.repository.saveSession(creator.uid, session(creator, joinId)).getOrThrow()
      sync(creator)
      val start = CompletableDeferred<Unit>()
      listOf(member, outsider)
          .map { client ->
            async(Dispatchers.IO) {
              start.await()
              client.repository.joinSession(client.uid, joinId)
            }
          }
          .also { jobs ->
            start.complete(Unit)
            jobs.awaitAll().forEach { it.getOrThrow() }
          }
      sync(member)
      sync(outsider)
      assertEquals(
          setOf(creator.uid, member.uid, outsider.uid),
          outsider.repository.getSession(joinId).getOrThrow().memberIds.toSet(),
      )

      val mixedId = uniqueId()
      val original = session(creator, mixedId)
      creator.repository.saveSession(creator.uid, original).getOrThrow()
      member.repository.joinSession(member.uid, mixedId).getOrThrow()
      sync(member)
      val gate = CompletableDeferred<Unit>()
      val join =
          async(Dispatchers.IO) {
            gate.await()
            outsider.repository.joinSession(outsider.uid, mixedId)
          }
      val leave =
          async(Dispatchers.IO) {
            gate.await()
            member.repository.leaveSession(member.uid, mixedId)
          }
      gate.complete(Unit)
      join.await().getOrThrow()
      leave.await().getOrThrow()
      sync(outsider)
      sync(member)
      assertEquals(
          setOf(creator.uid, outsider.uid),
          outsider.repository.getSession(mixedId).getOrThrow().memberIds.toSet(),
      )
    }
  }

  /** Creators delete sessions, missing deletes succeed, and other users cannot delete. */
  @Test
  fun deleteEnforcesCreatorAndIsIdempotentForMissingSessions() = runBlocking {
    val original = session(creator, uniqueId())
    creator.repository.saveSession(creator.uid, original).getOrThrow()
    sync(creator)
    val before = serverData(outsider, original.id)

    assertError(
        member.repository.deleteSession(member.uid, original.id),
        EventsError.PERMISSION_DENIED,
    )
    assertEquals(before, serverData(outsider, original.id))

    creator.repository.deleteSession(creator.uid, original.id).getOrThrow()
    creator.repository.deleteSession(creator.uid, uniqueId()).getOrThrow()
    sync(creator)
    assertFalse(serverDocument(outsider, original.id).exists())
  }

  /** Offline save, join, leave, and delete fail promptly and never synchronize later. */
  @Test
  fun offlineMutationsFailWithoutQueueingWrites() = runBlocking {
    val created = session(creator, uniqueId())
    creator.repository.saveSession(creator.uid, created).getOrThrow()
    member.repository.joinSession(member.uid, created.id).getOrThrow()
    sync(member)
    val original = created.copy(memberIds = listOf(creator.uid, member.uid))
    val before = serverData(outsider, original.id)

    assertOfflineFailure(creator) {
      creator.repository.saveSession(
          creator.uid,
          original.copy(subject = "Offline edit", updatedAt = now().plusSeconds(1)),
      )
    }
    assertEquals(before, serverData(outsider, original.id))

    assertOfflineFailure(outsider) { outsider.repository.joinSession(outsider.uid, original.id) }
    assertEquals(before, serverData(outsider, original.id))

    assertOfflineFailure(member) { member.repository.leaveSession(member.uid, original.id) }
    assertEquals(before, serverData(outsider, original.id))

    assertOfflineFailure(creator) { creator.repository.deleteSession(creator.uid, original.id) }
    assertEquals(before, serverData(outsider, original.id))
  }

  /** Configures one emulator Auth user and an isolated Firestore client. */
  private suspend fun newClient(label: String): Client {
    val appName = "group-sessions-$label-${UUID.randomUUID()}"
    val app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setProjectId("demo-didyoustudy")
                .setApplicationId("1:123:android:groupsessions")
                .setApiKey("fake-${UUID.randomUUID()}")
                .build(),
            appName,
        )
    val auth = FirebaseAuth.getInstance(app)
    auth.useEmulator("10.0.2.2", 9099)
    val db = FirebaseFirestore.getInstance(app)
    db.useEmulator("10.0.2.2", 8080)
    val account = timed {
      auth
          .createUserWithEmailAndPassword(
              "$appName@example.test",
              "EmulatorOnly123!",
          )
          .await()
    }
    return Client(
            app,
            auth,
            db,
            account.user!!.uid,
            GroupSessionsRepositoryFirestore(db, auth),
        )
        .also(clients::add)
  }

  /** Builds a session whose default schedule is in the future relative to the test run. */
  private fun session(
      owner: Client,
      id: String,
      startsAt: Instant = now().plusSeconds(3600),
      endsAt: Instant = startsAt.plusSeconds(3600),
  ) =
      GroupSession(
          id = id,
          createdBy = owner.uid,
          memberIds = listOf(owner.uid),
          type = EventType.COURSE,
          subject = "Analysis",
          startsAt = startsAt,
          endsAt = endsAt,
          createdAt = now(),
      )

  /** Waits until all local writes for [client] have reached Firestore. */
  private suspend fun sync(client: Client) {
    timed { client.db.waitForPendingWrites().await() }
  }

  /** Disables the network, checks a bounded storage failure, and reconnects the client. */
  private suspend fun assertOfflineFailure(
      client: Client,
      operation: suspend () -> Result<Unit>,
  ) {
    client.db.disableNetwork().await()
    try {
      val result = withTimeout(TIMEOUT_MILLIS) { operation() }
      assertError(result, EventsError.STORAGE_FAILURE)
    } finally {
      client.db.enableNetwork().await()
      sync(client)
    }
  }

  /** Reads a document from the server, bypassing this client's local cache. */
  private suspend fun serverData(client: Client, id: String): Map<String, Any?> = timed {
    serverDocument(client, id).data!!
  }

  /** Reads a document snapshot directly from the server. */
  private suspend fun serverDocument(client: Client, id: String) = timed {
    client.db
        .collection(GroupSessionFirestoreMapper.COLLECTION)
        .document(id)
        .get(Source.SERVER)
        .await()
  }

  /** Asserts that a rejected create left no server document behind. */
  private suspend fun assertMissing(client: Client, id: String) {
    assertFalse(serverDocument(client, id).exists())
  }

  /** Asserts a failed repository result has the expected domain error. */
  private fun assertError(result: Result<*>, expected: EventsError) {
    assertTrue(result.isFailure)
    assertEquals(expected, (result.exceptionOrNull() as EventsException).error)
  }

  /** Bounds network operations so an emulator failure cannot hang instrumentation. */
  private suspend fun <T> timed(action: suspend () -> T): T =
      withTimeout(TIMEOUT_MILLIS) { action() }

  /** Uses one-second precision for stable Firestore timestamp round-trips. */
  private fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)

  /** Converts an instant to a Firestore timestamp. */
  private fun timestamp(value: Instant) = Timestamp(value.epochSecond, value.nano)

  /** Returns a unique document ID for test isolation. */
  private fun uniqueId(): String = UUID.randomUUID().toString()

  private companion object {
    const val TIMEOUT_MILLIS = 15_000L
  }
}
