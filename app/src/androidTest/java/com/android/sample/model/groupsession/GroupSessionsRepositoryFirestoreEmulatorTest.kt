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
      var uid: String,
      val repository: GroupSessionsRepositoryFirestore,
  )

  private data class UnreachableClient(
      val app: FirebaseApp,
      val db: FirebaseFirestore,
      val repository: GroupSessionsRepositoryFirestore,
  )

  private val clients = mutableListOf<Client>()
  private val unreachableClients = mutableListOf<UnreachableClient>()
  private lateinit var creator: Client
  private lateinit var member: Client
  private lateinit var outsider: Client

  /** Creates independent Auth and Firestore emulator clients for the session participants. */
  @Before
  fun setUp() = runBlocking {
    clients.clear()
    unreachableClients.clear()
    creator = newClient("creator")
    member = newClient("member")
    outsider = newClient("outsider")
  }

  /** Releases every isolated Firebase client and its local Firestore cache. */
  @After
  fun tearDown() = runBlocking {
    unreachableClients.forEach { client ->
      try {
        client.db.terminate().await()
        client.db.clearPersistence().await()
      } finally {
        client.app.delete()
      }
    }
    unreachableClients.clear()
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

  /** A malformed stored session does not hide a valid session from public observation. */
  @Test
  fun observeSessionsSkipsUndecodableSession() = runBlocking {
    val valid = session(creator, uniqueId())
    val badId = uniqueId()
    try {
      creator.repository.saveSession(creator.uid, valid).getOrThrow()
      sync(creator)
      writeUndecodableSession(badId)

      val emission =
          withTimeout(TIMEOUT_MILLIS) {
            outsider.repository.observeSessions(now()).first { result ->
              result.isFailure || result.getOrNull()?.any { it.id == valid.id } == true
            }
          }

      assertTrue(emission.isSuccess)
      assertTrue(emission.getOrThrow().any { it.id == valid.id })
      assertFalse(emission.getOrThrow().any { it.id == badId })
    } finally {
      deleteDocument(creator, badId)
      deleteDocument(creator, valid.id)
    }
  }

  /** Reading an individually malformed document reports INVALID_EVENT_DATA. */
  @Test
  fun getUndecodableSessionReturnsInvalidEventData() = runBlocking {
    val badId = uniqueId()
    try {
      writeUndecodableSession(badId)

      assertError(outsider.repository.getSession(badId), EventsError.INVALID_EVENT_DATA)
    } finally {
      deleteDocument(creator, badId)
    }
  }

  /** A creator can delete a malformed session and it is gone afterward. */
  @Test
  fun creatorCanDeleteUndecodableSession() = runBlocking {
    val badId = uniqueId()
    try {
      writeUndecodableSession(badId)

      creator.repository.deleteSession(creator.uid, badId).getOrThrow()
      assertFalse(serverDocument(outsider, badId).exists())
    } finally {
      deleteDocument(creator, badId)
    }
  }

  /** A non-creator is denied deletion of a malformed session and it remains stored. */
  @Test
  fun nonCreatorCannotDeleteUndecodableSession() = runBlocking {
    val badId = uniqueId()
    try {
      writeUndecodableSession(badId)

      assertError(
          member.repository.deleteSession(member.uid, badId),
          EventsError.PERMISSION_DENIED,
      )
      assertTrue(serverDocument(outsider, badId).exists())
    } finally {
      deleteDocument(creator, badId)
    }
  }

  /** Public observations filter end boundaries and sort by start, end, then document ID. */
  @Test
  fun observeSessionsFiltersAndSortsForAnotherUser() = runBlocking {
    val base = now()
    val threshold = base.plusSeconds(1800)
    val prefix = uniqueId()
    val sameTimeZ = session(creator, "$prefix-z", base.plusSeconds(3600), base.plusSeconds(7200))
    val sameTimeA = session(creator, "$prefix-a", base.plusSeconds(3600), base.plusSeconds(7200))
    val longer = session(creator, "$prefix-longer", base.plusSeconds(3600), base.plusSeconds(10800))
    val later = session(creator, "$prefix-later", base.plusSeconds(7200), base.plusSeconds(10800))
    val endingAt = session(creator, "$prefix-ending-at", threshold.minusSeconds(3600), threshold)
    val endingBefore =
        session(
            creator,
            "$prefix-ending-before",
            threshold.minusSeconds(3600),
            threshold.minusSeconds(1),
        )
    listOf(later, endingAt, sameTimeZ, endingBefore, longer, sameTimeA).forEach {
      creator.repository.saveSession(creator.uid, it).getOrThrow()
    }
    sync(creator)

    val expected = listOf(sameTimeA, sameTimeZ, longer, later)
    val testIds =
        listOf(later, endingAt, sameTimeZ, endingBefore, longer, sameTimeA).map { it.id }.toSet()
    val observed =
        withTimeout(TIMEOUT_MILLIS) {
          outsider.repository.observeSessions(threshold).first().getOrThrow()
        }
    assertEquals(expected, observed.filter { it.id in testIds })
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

    val initial =
        withTimeout(TIMEOUT_MILLIS) {
          flow.first { result -> result.getOrThrow().any { it.id == original.id } }.getOrThrow()
        }
    assertEquals(original, initial.first { it.id == original.id })

    val joined =
        async(Dispatchers.IO) {
          withTimeout(TIMEOUT_MILLIS) {
            flow.first { result ->
              result
                  .getOrThrow()
                  .firstOrNull { it.id == original.id }
                  ?.memberIds
                  ?.contains(outsider.uid) == true
            }
          }
        }
    outsider.repository.joinSession(outsider.uid, original.id).getOrThrow()
    sync(outsider)
    assertTrue(
        joined.await().getOrThrow().first { it.id == original.id }.memberIds.contains(outsider.uid)
    )

    val left =
        async(Dispatchers.IO) {
          withTimeout(TIMEOUT_MILLIS) {
            flow.first { result ->
              result
                  .getOrThrow()
                  .firstOrNull { it.id == original.id }
                  ?.memberIds
                  ?.contains(member.uid) == false
            }
          }
        }
    member.repository.leaveSession(member.uid, original.id).getOrThrow()
    sync(member)
    assertFalse(
        left.await().getOrThrow().first { it.id == original.id }.memberIds.contains(member.uid)
    )

    val edited = original.copy(subject = "Updated", updatedAt = now().plusSeconds(10))
    val edit =
        async(Dispatchers.IO) {
          withTimeout(TIMEOUT_MILLIS) {
            flow.first { result ->
              result.getOrThrow().firstOrNull { it.id == original.id }?.subject == edited.subject
            }
          }
        }
    creator.repository.saveSession(creator.uid, edited).getOrThrow()
    sync(creator)
    assertEquals(edited.subject, edit.await().getOrThrow().first { it.id == original.id }.subject)
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
    val memberIds = mutableSetOf(creator.uid)
    listOf(member, outsider).forEach { client ->
      client.repository.joinSession(client.uid, original.id).getOrThrow()
      sync(client)
      memberIds += client.uid
    }
    val filler = newClient("filler")
    repeat(GroupSession.MAX_MEMBERS - memberIds.size) { index ->
      val fillerId = signUp(filler, "filler-$index")
      filler.repository.joinSession(fillerId, original.id).getOrThrow()
      sync(filler)
      memberIds += fillerId
    }
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

  /** Two users racing for the final place yield exactly one successful join. */
  @Test
  fun concurrentJoinsForLastPlaceAllowExactlyOneWinner() = runBlocking {
    val filler = newClient("last-place-filler")
    val sessionIds = mutableListOf<String>()
    try {
      repeat(3) { round ->
        val value = session(creator, uniqueId())
        sessionIds += value.id
        creator.repository.saveSession(creator.uid, value).getOrThrow()
        sync(creator)

        val memberIds = mutableSetOf(creator.uid)
        repeat(GroupSession.MAX_MEMBERS - 2) { index ->
          val fillerId = signUp(filler, "last-place-$round-$index")
          filler.repository.joinSession(fillerId, value.id).getOrThrow()
          sync(filler)
          memberIds += fillerId
        }
        assertEquals(GroupSession.MAX_MEMBERS - 1, memberIds.size)

        val joiners = listOf(member, outsider)
        val start = CompletableDeferred<Unit>()
        val results =
            joiners
                .map { client ->
                  async(Dispatchers.IO) {
                    start.await()
                    client.repository.joinSession(client.uid, value.id)
                  }
                }
                .also { start.complete(Unit) }
                .awaitAll()

        assertEquals(1, results.count { it.isSuccess })
        val failure = results.single { it.isFailure }
        assertError(failure, EventsError.PERMISSION_DENIED)
        val winnerId = joiners[results.indexOfFirst { it.isSuccess }].uid
        val loserId = joiners[results.indexOfFirst { it.isFailure }].uid
        sync(member)
        sync(outsider)

        val storedMembers = serverData(creator, value.id)["memberIds"] as List<*>
        assertEquals(GroupSession.MAX_MEMBERS, storedMembers.size)
        assertTrue(storedMembers.contains(winnerId))
        assertFalse(storedMembers.contains(loserId))
      }
    } finally {
      sessionIds.forEach { deleteDocument(creator, it) }
    }
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

  /** Mutations through clients pointed at an unreachable port fail without changing server data. */
  @Test
  fun offlineMutationsFailWithoutQueueingWrites() = runBlocking {
    val created = session(creator, uniqueId())
    creator.repository.saveSession(creator.uid, created).getOrThrow()
    member.repository.joinSession(member.uid, created.id).getOrThrow()
    sync(member)
    val original = created.copy(memberIds = listOf(creator.uid, member.uid))
    val before = serverData(outsider, original.id)

    assertUnreachableFailure(creator, "save") { repository ->
      repository.saveSession(
          creator.uid,
          original.copy(subject = "Offline edit", updatedAt = now().plusSeconds(1)),
      )
    }
    assertEquals(before, serverData(outsider, original.id))

    assertUnreachableFailure(outsider, "join") { repository ->
      repository.joinSession(outsider.uid, original.id)
    }
    assertEquals(before, serverData(outsider, original.id))

    assertUnreachableFailure(member, "leave") { repository ->
      repository.leaveSession(member.uid, original.id)
    }
    assertEquals(before, serverData(outsider, original.id))

    assertUnreachableFailure(creator, "delete") { repository ->
      repository.deleteSession(creator.uid, original.id)
    }
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

  /** Creates an authenticated-repository client whose Firestore endpoint cannot be reached. */
  private fun newUnreachableClient(client: Client, label: String): UnreachableClient {
    val appName = "group-sessions-unreachable-$label-${UUID.randomUUID()}"
    val app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setProjectId("demo-didyoustudy")
                .setApplicationId("1:123:android:groupsessions-offline")
                .setApiKey("fake-${UUID.randomUUID()}")
                .build(),
            appName,
        )
    val db = FirebaseFirestore.getInstance(app)
    db.useEmulator("10.0.2.2", 1)
    return UnreachableClient(app, db, GroupSessionsRepositoryFirestore(db, client.auth))
        .also(unreachableClients::add)
  }

  /** Registers another emulator account on a reusable Firebase Auth client. */
  private suspend fun signUp(client: Client, label: String): String {
    val appName = "group-sessions-$label-${UUID.randomUUID()}"
    val uid = timed {
      client.auth
          .createUserWithEmailAndPassword("$appName@example.test", "EmulatorOnly123!")
          .await()
          .user!!
          .uid
    }
    client.uid = uid
    return uid
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

  /** Writes a rule-valid but undecodable payload directly, bypassing the repository mapper. */
  private suspend fun writeUndecodableSession(id: String) {
    val payload = GroupSessionFirestoreMapper.encode(session(creator, id)) + ("subject" to "\u00A0")
    timed {
      creator.db
          .collection(GroupSessionFirestoreMapper.COLLECTION)
          .document(id)
          .set(payload)
          .await()
    }
  }

  /** Deletes one document directly so cleanup also works when repository decoding fails. */
  private suspend fun deleteDocument(client: Client, id: String) {
    timed {
      client.db.collection(GroupSessionFirestoreMapper.COLLECTION).document(id).delete().await()
    }
  }

  /** Requires an unreachable-port operation to fail with STORAGE_FAILURE within 30 seconds. */
  private suspend fun assertUnreachableFailure(
      client: Client,
      label: String,
      operation: suspend (GroupSessionsRepositoryFirestore) -> Result<Unit>,
  ) {
    val unreachable = newUnreachableClient(client, label)
    val result = withTimeout(30_000) { operation(unreachable.repository) }
    assertError(result, EventsError.STORAGE_FAILURE)
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
