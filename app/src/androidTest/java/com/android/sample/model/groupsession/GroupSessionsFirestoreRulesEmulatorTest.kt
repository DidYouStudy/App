// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.groupsession

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventTextLimits
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies group-session access rules against the Auth and Firestore emulators. */
@RunWith(AndroidJUnit4::class)
class GroupSessionsFirestoreRulesEmulatorTest {
  private data class Client(
      val app: FirebaseApp,
      val auth: FirebaseAuth,
      val db: FirebaseFirestore,
      var uid: String,
  )

  private val clients = mutableListOf<Client>()
  private lateinit var creator: Client
  private lateinit var member: Client
  private lateinit var outsider: Client
  private lateinit var filler: Client
  private val start = Instant.ofEpochSecond(Instant.now().epochSecond).plusSeconds(24 * 3600)
  private val end = start.plusSeconds(3600)
  private val created = Instant.parse("2026-10-19T12:00:00Z")

  /** Creates distinct signed-in clients for the three session roles. */
  @Before
  fun setup() = runBlocking {
    clients.clear()
    creator = newClient("creator")
    member = newClient("member")
    outsider = newClient("outsider")
    filler = newClient("filler")
  }

  /** Releases every isolated Firebase client, including temporary members. */
  @After
  fun cleanup() = runBlocking {
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

  /** OTHER sessions allow a null subject and a duration of exactly 24 hours. */
  @Test
  fun creatorCanCreateAndEditOtherSessionAndExactDaySession() = runBlocking {
    val otherId = uniqueId()
    val other =
        sessionData(creator) +
            mapOf(
                "type" to EventType.OTHER.name,
                "subject" to null,
            )
    set(document(creator, otherId), other)
    val editedOther = other + ("description" to "Open study time")
    edit(document(creator, otherId), mapOf("description" to "Open study time"))
    assertEquals(withoutTimes(editedOther), withoutTimes(readData(outsider, otherId)))

    val dayId = uniqueId()
    val exactDay =
        sessionData(creator) +
            mapOf(
                "startsAt" to timestamp(start),
                "endsAt" to timestamp(start.plusSeconds(24 * 3600)),
            )
    set(document(creator, dayId), exactDay)
    assertEquals(withoutTimes(exactDay), withoutTimes(readData(outsider, dayId)))
  }

  /** Any signed-in non-member can read a document and list the collection. */
  @Test
  fun signedInOutsiderCanReadAndList() = runBlocking {
    val id = createSession()
    val expected = sessionData(creator)

    assertEquals(withoutTimes(expected), withoutTimes(readData(outsider, id)))
    val listed = timed {
      outsider.db.collection(GroupSessionFirestoreMapper.COLLECTION).get(Source.SERVER).await()
    }
    assertTrue(listed.documents.any { it.id == id })
    assertEquals(listOf(creator.uid), readData(outsider, id)["memberIds"])
  }

  /** The creator can edit schedule details and delete the session. */
  @Test
  fun creatorCanEditAndDeleteSession() = runBlocking {
    val id = createSession()
    val changes =
        mapOf(
            "startsAt" to timestamp(start.plusSeconds(7200)),
            "endsAt" to timestamp(end.plusSeconds(7200)),
            "subject" to "Physics",
            "location" to "Library",
            "description" to "Problem set",
        )

    edit(document(creator, id), changes)
    assertEquals(withoutTimes(sessionData(creator) + changes), withoutTimes(readData(member, id)))
    timed { document(creator, id).delete().await() }
    val deleted = timed { document(member, id).get(Source.SERVER).await() }
    assertFalse(deleted.exists())
  }

  /** Signed-in users can delete missing sessions, but signed-out users cannot. */
  @Test
  fun signedInUserCanDeleteMissingSessionButSignedOutUserCannot() = runBlocking {
    timed { document(outsider, uniqueId()).delete().await() }

    val signedOut = newSignedOutClient("signed-out")
    denied { timed { document(signedOut, uniqueId()).delete().await() } }
  }

  /** A member can join and later remove only their own ID. */
  @Test
  fun memberCanJoinAndLeaveThemself() = runBlocking {
    val id = createSession()
    val ref = document(member, id)

    update(ref, mapOf("memberIds" to listOf(creator.uid, member.uid)))
    assertEquals(listOf(creator.uid, member.uid), readData(outsider, id)["memberIds"])
    update(ref, mapOf("memberIds" to listOf(creator.uid)))
    assertEquals(listOf(creator.uid), readData(outsider, id)["memberIds"])
  }

  /** Existing members can leave an ended session, but outsiders cannot join it. */
  @Test
  fun outsiderCannotJoinAnEndedSessionButMemberCanLeave() = runBlocking {
    val id = createSession()
    update(
        document(member, id),
        mapOf("memberIds" to FieldValue.arrayUnion(member.uid)),
    )
    val joinedMembers = listOf(creator.uid, member.uid)

    val endedAt = Instant.ofEpochSecond(Instant.now().epochSecond - 3600)
    edit(
        document(creator, id),
        mapOf(
            "startsAt" to timestamp(endedAt.minusSeconds(3600)),
            "endsAt" to timestamp(endedAt),
        ),
    )

    denied {
      update(
          document(outsider, id),
          mapOf("memberIds" to (joinedMembers + outsider.uid)),
      )
    }
    assertEquals(joinedMembers, readData(outsider, id)["memberIds"])

    denied {
      update(
          document(outsider, id),
          mapOf("memberIds" to FieldValue.arrayUnion(outsider.uid)),
      )
    }
    assertEquals(joinedMembers, readData(outsider, id)["memberIds"])

    edit(document(creator, id), mapOf("description" to "Session has ended"))
    assertEquals("Session has ended", readData(outsider, id)["description"])

    update(
        document(member, id),
        mapOf("memberIds" to FieldValue.arrayRemove(member.uid)),
    )
    assertEquals(listOf(creator.uid), readData(outsider, id)["memberIds"])
  }

  /** A member can join and leave using server-side array transforms. */
  @Test
  fun memberCanJoinAndLeaveWithArrayTransforms() = runBlocking {
    val id = createSession()
    val ref = document(member, id)

    update(ref, mapOf("memberIds" to FieldValue.arrayUnion(member.uid)))
    assertEquals(listOf(creator.uid, member.uid), readData(outsider, id)["memberIds"])
    update(ref, mapOf("memberIds" to FieldValue.arrayRemove(member.uid)))
    assertEquals(listOf(creator.uid), readData(outsider, id)["memberIds"])
  }

  /** Invalid server-side array transforms are denied without changing membership. */
  @Test
  fun invalidArrayTransformsAreDeniedWithoutChangingMembership() = runBlocking {
    val id = createSession()
    update(
        document(member, id),
        mapOf("memberIds" to FieldValue.arrayUnion(member.uid)),
    )
    val expectedMembers = listOf(creator.uid, member.uid)

    denied {
      update(
          document(member, id),
          mapOf("memberIds" to FieldValue.arrayUnion(outsider.uid)),
      )
    }
    assertEquals(expectedMembers, readData(outsider, id)["memberIds"])

    denied {
      update(
          document(member, id),
          mapOf("memberIds" to FieldValue.arrayRemove(creator.uid)),
      )
    }
    assertEquals(expectedMembers, readData(outsider, id)["memberIds"])

    denied {
      update(
          document(member, id),
          mapOf("memberIds" to FieldValue.arrayUnion(member.uid)),
      )
    }
    assertEquals(expectedMembers, readData(outsider, id)["memberIds"])

    denied {
      update(
          document(outsider, id),
          mapOf("memberIds" to FieldValue.arrayRemove(outsider.uid)),
      )
    }
    assertEquals(expectedMembers, readData(outsider, id)["memberIds"])

    denied {
      update(
          document(creator, id),
          mapOf("memberIds" to FieldValue.arrayUnion(outsider.uid)),
      )
    }
    assertEquals(expectedMembers, readData(outsider, id)["memberIds"])

    denied {
      update(
          document(creator, id),
          mapOf("memberIds" to FieldValue.arrayRemove(member.uid)),
      )
    }
    assertEquals(expectedMembers, readData(outsider, id)["memberIds"])

    denied {
      update(
          document(creator, id),
          mapOf("memberIds" to FieldValue.arrayRemove(creator.uid)),
      )
    }
    assertEquals(expectedMembers, readData(outsider, id)["memberIds"])
  }

  /** A creator can edit details after members join but cannot replace their list. */
  @Test
  fun creatorCanEditDetailsButCannotReplaceJoinedMembers() = runBlocking {
    val id = createSession()
    update(
        document(member, id),
        mapOf("memberIds" to FieldValue.arrayUnion(member.uid)),
    )
    val expectedMembers = listOf(creator.uid, member.uid)
    val description = "Updated group session"
    val createdAt = readData(outsider, id)["createdAt"] as Timestamp

    edit(document(creator, id), mapOf("description" to description))
    val updatedData = readData(outsider, id)
    assertEquals(description, updatedData["description"])
    assertTrue((updatedData["updatedAt"] as Timestamp) > createdAt)
    assertEquals(expectedMembers, updatedData["memberIds"])

    // A full replacement with valid times is denied only because its member list is stale.
    val beforeReplacement = readData(outsider, id)
    denied { set(document(creator, id), sessionData(creator) + ("createdAt" to createdAt)) }
    assertEquals(beforeReplacement, readData(outsider, id))
  }

  /** A thirtieth member can join, while a thirty-first member is rejected. */
  @Test
  fun acceptsThirtiethMemberAndRejectsThirtyFirst() = runBlocking {
    val id = createSession()
    val memberIds = mutableListOf(creator.uid)
    repeat(28) { index ->
      memberIds += signUp(filler, "filler-$index")
      update(document(filler, id), mapOf("memberIds" to memberIds.toList()))
    }

    memberIds += member.uid
    update(document(member, id), mapOf("memberIds" to memberIds.toList()))
    assertEquals(memberIds, readData(outsider, id)["memberIds"])

    denied {
      update(
          document(outsider, id),
          mapOf("memberIds" to (memberIds + outsider.uid)),
      )
    }
    assertEquals(memberIds, readData(outsider, id)["memberIds"])

    denied {
      update(
          document(outsider, id),
          mapOf("memberIds" to FieldValue.arrayUnion(outsider.uid)),
      )
    }
    assertEquals(memberIds, readData(outsider, id)["memberIds"])
  }

  /**
   * The mapper's serialized document, with server times as the repository writes it, is accepted
   * and decodes with its path ID and the stored times.
   */
  @Test
  fun mapperPayloadCanBeCreatedAndReadBack() = runBlocking {
    val session =
        GroupSession(
            id = uniqueId(),
            createdBy = creator.uid,
            memberIds = listOf(creator.uid),
            details =
                EventDetails(
                    type = EventType.COURSE,
                    subject = "Analysis",
                    startsAt = start,
                    endsAt = end,
                ),
            createdAt = created,
        )
    val data = GroupSessionFirestoreMapper.encode(session)

    // The phone's own times are rejected.
    denied { set(document(creator, session.id), data) }
    set(document(creator, session.id), data + serverTimes())
    val stored = readData(member, session.id)
    val storedTimestamp = stored["createdAt"] as Timestamp
    val storedTime =
        Instant.ofEpochSecond(storedTimestamp.seconds, storedTimestamp.nanoseconds.toLong())
    assertEquals(stored["createdAt"], stored["updatedAt"])
    assertNotEquals(timestamp(created), stored["createdAt"])
    assertEquals(
        session.copy(createdAt = storedTime, updatedAt = storedTime),
        GroupSessionFirestoreMapper.decode(session.id, stored),
    )
  }

  /** Creation and edit times must be the server's time; a phone-chosen time is denied. */
  @Test
  fun phoneChosenTimesAreDenied() = runBlocking {
    val phoneTime = timestamp(Instant.ofEpochSecond(Instant.now().epochSecond))
    listOf(
            sessionData(creator) + ("createdAt" to phoneTime),
            sessionData(creator) + ("updatedAt" to phoneTime),
            sessionData(creator) + mapOf("createdAt" to phoneTime, "updatedAt" to phoneTime),
        )
        .forEach { data -> denied { set(document(creator, uniqueId()), data) } }

    val id = createSession()
    val before = readData(outsider, id)
    // An edit must refresh updatedAt with the server time.
    denied { update(document(creator, id), mapOf("description" to "No new time")) }
    denied {
      update(document(creator, id), mapOf("description" to "Phone time", "updatedAt" to phoneTime))
    }
    assertEquals(before, readData(outsider, id))
  }

  /** Joining and leaving do not need, and may not set, a new updatedAt. */
  @Test
  fun joinAndLeaveKeepUpdatedAt() = runBlocking {
    val id = createSession()
    val updatedAt = readData(outsider, id)["updatedAt"]

    update(document(member, id), mapOf("memberIds" to FieldValue.arrayUnion(member.uid)))
    assertEquals(updatedAt, readData(outsider, id)["updatedAt"])
    denied {
      update(
          document(member, id),
          mapOf(
              "memberIds" to FieldValue.arrayRemove(member.uid),
              "updatedAt" to FieldValue.serverTimestamp(),
          ),
      )
    }
    update(document(member, id), mapOf("memberIds" to FieldValue.arrayRemove(member.uid)))
    assertEquals(updatedAt, readData(outsider, id)["updatedAt"])
  }

  /** Text exactly at the Kotlin limits is accepted on create and edit, counting characters. */
  @Test
  fun acceptsTextAtTheLimits() = runBlocking {
    // "é" is two bytes in UTF-8: this proves the rules count characters, as the app does.
    val atLimits =
        mapOf(
            "subject" to "é".repeat(EventTextLimits.SUBJECT),
            "location" to "é".repeat(EventTextLimits.LOCATION),
            "description" to "é".repeat(EventTextLimits.DESCRIPTION),
        )
    val id = uniqueId()
    set(document(creator, id), sessionData(creator) + atLimits)
    assertEquals(atLimits, readData(outsider, id).filterKeys { it in atLimits })

    val editId = createSession()
    edit(document(creator, editId), atLimits)
    assertEquals(atLimits, readData(outsider, editId).filterKeys { it in atLimits })
  }

  /**
   * A session stored before the length limits, with text over them, can still be joined and left.
   * Its creator can edit it only by bringing the text back within the limits.
   */
  @Test
  fun overLimitStoredSessionCanBeJoinedAndLeft() = runBlocking {
    val id = uniqueId()
    writeBypassingRules(
        id,
        mapOf(
            "subject" to "a".repeat(EventTextLimits.SUBJECT + 1),
            "location" to "a".repeat(EventTextLimits.LOCATION + 1),
            "description" to "a".repeat(EventTextLimits.DESCRIPTION + 1),
        ),
    )

    update(document(member, id), mapOf("memberIds" to FieldValue.arrayUnion(member.uid)))
    assertEquals(listOf(creator.uid, member.uid), readData(outsider, id)["memberIds"])
    update(document(member, id), mapOf("memberIds" to FieldValue.arrayRemove(member.uid)))
    assertEquals(listOf(creator.uid), readData(outsider, id)["memberIds"])

    denied { edit(document(creator, id), mapOf("type" to EventType.LAB.name)) }
    val shortened = mapOf("subject" to "Analysis", "location" to "BC 01", "description" to "Short")
    edit(document(creator, id), shortened)
    assertEquals(shortened, readData(outsider, id).filterKeys { it in shortened })
  }

  /** Signed-out clients cannot read, list, create, update or delete. */
  @Test
  fun signedOutClientCannotAccessSessions() = runBlocking {
    val id = createSession()
    creator.auth.signOut()

    denied { timed { document(creator, id).get(Source.SERVER).await() } }
    denied {
      timed {
        creator.db.collection(GroupSessionFirestoreMapper.COLLECTION).get(Source.SERVER).await()
      }
    }
    denied { set(document(creator, uniqueId()), sessionData(creator)) }
    denied { update(document(creator, id), mapOf("description" to "Changed")) }
    denied { timed { document(creator, id).delete().await() } }
  }

  /** Non-creators cannot edit session fields or delete a session. */
  @Test
  fun nonCreatorCannotEditDetailsOrDelete() = runBlocking {
    val id = createSession()
    val changes =
        listOf(
            "type" to EventType.PROJECT.name,
            "subject" to "Physics",
            "startsAt" to timestamp(start.plusSeconds(60)),
            "endsAt" to timestamp(end.plusSeconds(60)),
            "location" to "Library",
            "description" to "Changed",
        )

    // Even with a valid server edit time, a non-creator cannot edit.
    changes.forEach { (field, value) ->
      denied { edit(document(member, id), mapOf(field to value)) }
    }
    denied { edit(document(member, id), emptyMap()) }
    denied { timed { document(member, id).delete().await() } }
  }

  /** Non-creators cannot alter others' membership, duplicate their own ID or edit another field. */
  @Test
  fun nonCreatorCannotChangeOtherMembershipOrSessionFields() = runBlocking {
    val id = createSession()
    update(
        document(member, id),
        mapOf("memberIds" to listOf(creator.uid, member.uid)),
    )

    val originalDescription = readData(outsider, id)["description"]
    val beforeOutsiderEdit = readData(outsider, id)["memberIds"]
    denied {
      update(
          document(outsider, id),
          mapOf("memberIds" to listOf(creator.uid)),
      )
    }
    assertEquals(beforeOutsiderEdit, readData(outsider, id)["memberIds"])
    assertEquals(originalDescription, readData(outsider, id)["description"])

    denied {
      update(
          document(member, id),
          mapOf("memberIds" to listOf(creator.uid, member.uid, member.uid)),
      )
    }
    val beforeAddingSomeoneElse = readData(outsider, id)["memberIds"]
    denied {
      update(
          document(member, id),
          mapOf("memberIds" to listOf(creator.uid, member.uid, "someone-else")),
      )
    }
    assertEquals(beforeAddingSomeoneElse, readData(outsider, id)["memberIds"])

    denied {
      update(
          document(member, id),
          mapOf("memberIds" to listOf(member.uid, creator.uid)),
      )
    }
    assertEquals(listOf(creator.uid, member.uid), readData(outsider, id)["memberIds"])

    val beforeRemovingCreator = readData(outsider, id)["memberIds"]
    denied { update(document(member, id), mapOf("memberIds" to listOf(member.uid))) }
    assertEquals(beforeRemovingCreator, readData(outsider, id)["memberIds"])

    val beforeAddingOutsider = readData(outsider, id)["memberIds"]
    denied {
      update(
          document(outsider, id),
          mapOf("memberIds" to listOf(creator.uid, outsider.uid)),
      )
    }
    assertEquals(beforeAddingOutsider, readData(outsider, id)["memberIds"])

    val outsiderMembers = readData(outsider, id)["memberIds"]
    val outsiderDescription = readData(outsider, id)["description"]
    denied {
      update(
          document(outsider, id),
          mapOf(
              "memberIds" to listOf(creator.uid, member.uid, outsider.uid),
              "description" to "Changed with outsider join",
          ),
      )
    }
    assertEquals(outsiderMembers, readData(outsider, id)["memberIds"])
    assertEquals(outsiderDescription, readData(outsider, id)["description"])

    val memberMembers = readData(outsider, id)["memberIds"]
    val memberDescription = readData(outsider, id)["description"]
    denied {
      update(
          document(member, id),
          mapOf(
              "memberIds" to listOf(creator.uid),
              "description" to "Changed with member leave",
          ),
      )
    }
    assertEquals(memberMembers, readData(outsider, id)["memberIds"])
    assertEquals(memberDescription, readData(outsider, id)["description"])

    val beforeOutsiderAddingSomeoneElse = readData(outsider, id)["memberIds"]
    denied {
      update(
          document(outsider, id),
          mapOf("memberIds" to listOf(creator.uid, member.uid, outsider.uid, "someone-else")),
      )
    }
    assertEquals(beforeOutsiderAddingSomeoneElse, readData(outsider, id)["memberIds"])

    val beforeOutsiderAddingThemselfTwice = readData(outsider, id)["memberIds"]
    denied {
      update(
          document(outsider, id),
          mapOf("memberIds" to listOf(creator.uid, member.uid, outsider.uid, outsider.uid)),
      )
    }
    assertEquals(beforeOutsiderAddingThemselfTwice, readData(outsider, id)["memberIds"])

    val thirdId = signUp(filler, "third-member")
    update(
        document(filler, id),
        mapOf("memberIds" to listOf(creator.uid, member.uid, thirdId)),
    )
    val beforeMemberEdit = readData(outsider, id)["memberIds"]
    denied {
      update(
          document(member, id),
          mapOf("memberIds" to listOf(creator.uid, member.uid)),
      )
    }
    assertEquals(beforeMemberEdit, readData(outsider, id)["memberIds"])
  }

  /** The creator cannot change membership, createdBy or createdAt. */
  @Test
  fun creatorCannotChangeMembershipOrImmutableFields() = runBlocking {
    val id = createSession()
    update(
        document(member, id),
        mapOf("memberIds" to listOf(creator.uid, member.uid)),
    )

    val joinedMembers = readData(outsider, id)["memberIds"]
    // Edits below carry a valid server edit time, so only the rule under test can deny them.
    // The creator must remain a member.
    denied {
      edit(
          document(creator, id),
          mapOf("memberIds" to listOf(member.uid)),
      )
    }
    assertEquals(joinedMembers, readData(outsider, id)["memberIds"])

    val beforeCreatorRemovingMember = readData(outsider, id)["memberIds"]
    // The creator cannot change the member list.
    denied {
      edit(
          document(creator, id),
          mapOf("memberIds" to listOf(creator.uid)),
      )
    }
    assertEquals(beforeCreatorRemovingMember, readData(outsider, id)["memberIds"])

    val creatorOnlyId = createSession()
    // The creator must remain a member and a session cannot be empty.
    denied {
      edit(
          document(creator, creatorOnlyId),
          mapOf("memberIds" to emptyList<String>()),
      )
    }
    assertEquals(listOf(creator.uid), readData(outsider, creatorOnlyId)["memberIds"])

    val immutableChanges =
        listOf(
            "createdBy" to member.uid,
            "createdAt" to timestamp(created.plusSeconds(1)),
            "createdAt" to FieldValue.serverTimestamp(),
        )

    listOf(creator, member).forEach { actor ->
      immutableChanges.forEach { (field, value) ->
        denied { edit(document(actor, id), mapOf(field to value)) }
      }
    }
  }

  /**
   * Create and edit reject malformed types, subjects, schedules, locations, over-long text and
   * extra fields.
   */
  @Test
  fun rejectsInvalidDataOnCreateAndEdit() = runBlocking {
    val id = createSession()
    val newSession = sessionData(creator)
    // A full replacement that is valid apart from the change under test.
    val replacement = newSession + ("createdAt" to readData(outsider, id)["createdAt"])
    set(document(creator, id), replacement)
    val invalidChanges =
        listOf(
            "createdBy" to outsider.uid,
            "memberIds" to listOf(creator.uid, member.uid),
            "type" to 42,
            "type" to "UNKNOWN",
            "subject" to " ",
            "subject" to null,
            "subject" to "a".repeat(EventTextLimits.SUBJECT + 1),
            "endsAt" to timestamp(start),
            "endsAt" to timestamp(start.plusSeconds(24 * 3600 + 1)),
            "createdAt" to 42,
            "updatedAt" to 42,
            "startsAt" to 42,
            "location" to " ",
            "location" to "a".repeat(EventTextLimits.LOCATION + 1),
            "description" to 42,
            "description" to "a".repeat(EventTextLimits.DESCRIPTION + 1),
            "unexpected" to true,
        )

    invalidChanges.forEach { change ->
      denied { set(document(creator, uniqueId()), newSession + change) }
      denied { set(document(creator, id), replacement + change) }
    }
  }

  /** Creates an ordinary valid session as the creator before rule-denial assertions. */
  private suspend fun createSession(): String {
    val id = uniqueId()
    set(document(creator, id), sessionData(creator))
    return id
  }

  /** Creates an emulator user with a dedicated Firebase app, auth instance and Firestore client. */
  private suspend fun newClient(label: String): Client {
    val client = newSignedOutClient(label)
    client.uid = signUp(client.auth, client.app.name)
    return client
  }

  /**
   * Creates a dedicated Firebase app and Firestore client that has never signed in.
   *
   * Use it to test signed-out access. Signing out an existing client is racy for writes: Firestore
   * learns about the sign-out asynchronously, and a write queued before that is held for the
   * previous user and never completes.
   */
  private fun newSignedOutClient(label: String): Client {
    val name = "group-rules-$label-${UUID.randomUUID()}"
    val app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setProjectId("demo-didyoustudy")
                .setApplicationId("1:123:android:groupsessions")
                .setApiKey("fake-${UUID.randomUUID()}")
                .build(),
            name,
        )
    val auth = FirebaseAuth.getInstance(app)
    auth.useEmulator("10.0.2.2", 9099)
    val db = FirebaseFirestore.getInstance(app)
    db.useEmulator("10.0.2.2", 8080)
    return Client(app, auth, db, uid = "").also(clients::add)
  }

  /** Creates another unique emulator account on a reusable Firebase Auth client. */
  private suspend fun signUp(client: Client, label: String): String {
    val uid = signUp(client.auth, "group-rules-$label-${UUID.randomUUID()}")
    client.uid = uid
    return uid
  }

  /** Registers a user with a unique email address. */
  private suspend fun signUp(auth: FirebaseAuth, name: String): String = timed {
    auth.createUserWithEmailAndPassword("$name@example.test", "EmulatorOnly123!").await().user!!.uid
  }

  /**
   * Builds a new-session payload without depending on the mapper under test. Its times are server
   * timestamps, as the rules require.
   */
  private fun sessionData(owner: Client): Map<String, Any?> =
      mapOf(
          "createdBy" to owner.uid,
          "memberIds" to listOf(owner.uid),
          "type" to EventType.COURSE.name,
          "subject" to "Analysis",
          "startsAt" to timestamp(start),
          "endsAt" to timestamp(end),
          "location" to null,
          "description" to null,
      ) + serverTimes()

  /** Sets both creation and edit times to the server's time, as a new session must. */
  private fun serverTimes(): Map<String, Any?> =
      mapOf(
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
      )

  /** Drops the server-set times, which a test cannot predict, before comparing documents. */
  private fun withoutTimes(data: Map<String, Any?>): Map<String, Any?> =
      data - "createdAt" - "updatedAt"

  /** Resolves a session document for the specified authenticated user. */
  private fun document(client: Client, id: String): DocumentReference =
      client.db.collection(GroupSessionFirestoreMapper.COLLECTION).document(id)

  /** Reads the server document so cached values cannot mask a rules denial. */
  private suspend fun readData(client: Client, id: String): Map<String, Any?> = timed {
    document(client, id).get(Source.SERVER).await().data!!
  }

  /** Writes a document with a timeout. */
  private suspend fun set(ref: DocumentReference, data: Map<String, Any?>) {
    timed { ref.set(data).await() }
  }

  /** Updates a document with a timeout. */
  private suspend fun update(ref: DocumentReference, fields: Map<String, Any?>) {
    timed { ref.update(fields).await() }
  }

  /** Updates [fields] as a creator edit does, with the server's time as the new updatedAt. */
  private suspend fun edit(ref: DocumentReference, fields: Map<String, Any?>) {
    update(ref, fields + ("updatedAt" to FieldValue.serverTimestamp()))
  }

  /**
   * Stores a session owned by [creator] with [text] fields, skipping the security rules.
   *
   * The emulator's REST API accepts the `Bearer owner` token as an admin, which is how a document
   * written before a rule existed can be recreated. The client SDKs cannot skip the rules.
   */
  private suspend fun writeBypassingRules(id: String, text: Map<String, String>) {
    fun string(value: String) = JSONObject().put("stringValue", value)
    fun time(value: Instant) = JSONObject().put("timestampValue", value.toString())
    val fields =
        JSONObject()
            .put("createdBy", string(creator.uid))
            .put(
                "memberIds",
                JSONObject()
                    .put(
                        "arrayValue",
                        JSONObject().put("values", JSONArray().put(string(creator.uid))),
                    ),
            )
            .put("type", string(EventType.COURSE.name))
            .put("startsAt", time(start))
            .put("endsAt", time(end))
            .put("createdAt", time(created))
            .put("updatedAt", time(created))
    text.forEach { (field, value) -> fields.put(field, string(value)) }
    val url =
        URL(
            "http://10.0.2.2:8080/v1/projects/demo-didyoustudy/databases/(default)/documents/" +
                "${GroupSessionFirestoreMapper.COLLECTION}?documentId=$id"
        )
    withContext(Dispatchers.IO) {
      val connection = url.openConnection() as HttpURLConnection
      try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer owner")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use {
          it.write(JSONObject().put("fields", fields).toString().toByteArray())
        }
        val status = connection.responseCode
        check(status in 200..299) { "Admin write failed: HTTP $status" }
      } finally {
        connection.disconnect()
      }
    }
  }

  /** Requires an actual Firestore permission-denied response. */
  private suspend fun denied(action: suspend () -> Unit) {
    try {
      timed { action() }
      throw AssertionError("Expected server permission denial")
    } catch (error: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code)
    }
  }

  /** Bounds emulator operations instead of relying on sleeps. */
  private suspend fun <T> timed(action: suspend () -> T): T = withTimeout(15_000) { action() }

  /** Returns a unique document ID for isolation between operations and tests. */
  private fun uniqueId() = UUID.randomUUID().toString()

  /** Converts UTC instants to Firestore timestamps. */
  private fun timestamp(value: Instant) = Timestamp(value.epochSecond, value.nano)
}
