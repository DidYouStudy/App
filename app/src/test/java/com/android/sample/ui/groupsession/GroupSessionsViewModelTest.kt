// Co-authored-by: GitHub Copilot
package com.android.sample.ui.groupsession

import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import com.android.sample.model.groupsession.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import com.android.sample.ui.auth.TestAuthRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSessionsViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private val now = Instant.parse("2025-03-01T12:00:00Z")
  private lateinit var authRepository: TestAuthRepository
  private lateinit var repository: InMemoryGroupSessionsRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = TestAuthRepository()
    repository = InMemoryGroupSessionsRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** A signed-out user gets an empty, non-loading state without an error. */
  @Test
  fun signedOut_startsWithEmptyNonLoadingState() = runTest {
    val viewModel = createViewModel()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(emptyList<GroupSessionItem>(), viewModel.uiState.value.sessions)
    assertNull(viewModel.uiState.value.error)
  }

  /** Loading remains active until the repository produces its first list. */
  @Test
  fun signedIn_waitsForFirstListBeforeClearingLoading() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))

    assertTrue(viewModel.uiState.value.isLoading)
    emissions.emit(Result.success(listOf(session("first", ALICE))))

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(listOf("first"), viewModel.uiState.value.sessions.map { it.session.id })
  }

  /** Signing in after sign-out restores loading until the new session list arrives. */
  @Test
  fun signingInAgainShowsLoadingUntilTheNewListArrives() = runTest {
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))

    assertFalse(viewModel.uiState.value.isLoading)
    authRepository.currentUser.value = account(ALICE)
    assertTrue(viewModel.uiState.value.isLoading)

    val session = session("after-sign-in", ALICE)
    emissions.emit(Result.success(listOf(session)))

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(listOf(session), viewModel.uiState.value.sessions.map { it.session })
  }

  /** The list excludes ended sessions, retains repository order, and marks both relationships. */
  @Test
  fun list_filtersByCurrentTimeAndMarksMembershipAndCreator() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val own = session("own", ALICE, listOf(ALICE), startsAt = now.plusSeconds(60))
    val joined = session("joined", BOB, listOf(BOB, ALICE), startsAt = now.plusSeconds(120))
    val notJoined = session("not-joined", BOB, listOf(BOB), startsAt = now.plusSeconds(180))
    val ended =
        session(
            "ended",
            BOB,
            listOf(BOB),
            startsAt = now.minusSeconds(60),
            endsAt = now,
        )
    listOf(own, joined, notJoined, ended).forEach { repository.saveSession(it.createdBy, it) }

    val viewModel = createViewModel()

    assertEquals(
        listOf(
            GroupSessionItem(own, isMember = true, isCreator = true),
            GroupSessionItem(joined, isMember = true, isCreator = false),
            GroupSessionItem(notJoined, isMember = false, isCreator = false),
        ),
        viewModel.uiState.value.sessions,
    )
  }

  /** Repository saves and membership updates appear without calling a ViewModel refresh method. */
  @Test
  fun repositoryChangesUpdateLiveListAndMembership() = runTest {
    authRepository.currentUser.value = account(BOB)
    val existing = session("existing", ALICE, listOf(ALICE))
    repository.saveSession(ALICE, existing)
    val viewModel = createViewModel()

    assertEquals(false, viewModel.uiState.value.sessions.single().isMember)
    repository.joinSession(BOB, existing.id)
    assertTrue(viewModel.uiState.value.sessions.single().isMember)

    val added = session("added", BOB, listOf(BOB), startsAt = now.plusSeconds(120))
    repository.saveSession(BOB, added)
    assertEquals(
        listOf("existing", "added"),
        viewModel.uiState.value.sessions.map { it.session.id },
    )
  }

  /**
   * Search checks subject, location, and description, trims only for matching, and tolerates null
   * subjects.
   */
  @Test
  fun searchMatchesAllFieldsCaseInsensitivelyAndHandlesNullSubject() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val physics =
        session(
            "physics",
            ALICE,
            subject = "Physics",
            location = "Library",
            description = "Exam review",
        )
    val other =
        session(
            "other",
            ALICE,
            type = EventType.OTHER,
            subject = null,
            location = "Quiet Hall",
            description = "Group planning",
            startsAt = now.plusSeconds(120),
        )
    repository.saveSession(ALICE, physics)
    repository.saveSession(ALICE, other)
    val viewModel = createViewModel()

    viewModel.onQueryChange("  pHySiCs  ")
    assertEquals("  pHySiCs  ", viewModel.uiState.value.query)
    assertEquals(listOf("physics"), visibleIds(viewModel))

    viewModel.onQueryChange("LIBRARY")
    assertEquals(listOf("physics"), visibleIds(viewModel))
    viewModel.onQueryChange(" REVIEW ")
    assertEquals(listOf("physics"), visibleIds(viewModel))
    viewModel.onQueryChange("quiet hall")
    assertEquals(listOf("other"), visibleIds(viewModel))
    viewModel.onQueryChange("PLANNING")
    assertEquals(listOf("other"), visibleIds(viewModel))
    viewModel.onQueryChange("   ")
    assertEquals(listOf("physics", "other"), visibleIds(viewModel))
  }

  /** JOINED includes the creator, combines with search, and ALL restores non-member matches. */
  @Test
  fun joinedFilterCombinesWithSearchAndAllRestoresMatchingSessions() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val own = session("own-math", ALICE, subject = "Math")
    val joined =
        session(
            "joined-biology",
            BOB,
            listOf(BOB, ALICE),
            subject = "Biology",
            startsAt = now.plusSeconds(120),
        )
    val notJoined =
        session(
            "not-joined-biology",
            BOB,
            listOf(BOB),
            subject = "Biology",
            startsAt = now.plusSeconds(180),
        )
    listOf(own, joined, notJoined).forEach { repository.saveSession(it.createdBy, it) }
    val viewModel = createViewModel()

    viewModel.onFilterChange(GroupSessionFilter.JOINED)
    assertEquals(listOf("own-math", "joined-biology"), visibleIds(viewModel))
    viewModel.onQueryChange("biology")
    assertEquals(listOf("joined-biology"), visibleIds(viewModel))
    viewModel.onFilterChange(GroupSessionFilter.ALL)
    assertEquals(listOf("joined-biology", "not-joined-biology"), visibleIds(viewModel))
    viewModel.onQueryChange("")
    assertEquals(listOf("own-math", "joined-biology", "not-joined-biology"), visibleIds(viewModel))
  }

  /** Join and leave actions mutate repository membership and the corresponding visible item. */
  @Test
  fun joinAndLeaveUpdateMembershipThroughRepository() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val target = session("target", BOB, listOf(BOB))
    repository.saveSession(BOB, target)
    val viewModel = createViewModel()

    viewModel.join(target.id)
    assertTrue(viewModel.uiState.value.sessions.single().isMember)

    viewModel.leave(target.id)
    assertFalse(viewModel.uiState.value.sessions.single().isMember)
  }

  /**
   * Missing-session joins and creator leaves produce their stable categories and can be cleared.
   */
  @Test
  fun joinAndLeaveFailuresExposeCategoriesAndClearErrorResetsIt() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val own = session("own", ALICE)
    repository.saveSession(ALICE, own)
    val viewModel = createViewModel()

    viewModel.join("missing")
    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)
    viewModel.clearError()
    assertNull(viewModel.uiState.value.error)

    viewModel.leave(own.id)
    assertEquals(GroupSessionsError.LEAVE_FAILED, viewModel.uiState.value.error)
  }

  /** A failed session flow stops loading and exposes LOAD_FAILED without leaking exception text. */
  @Test
  fun failedSessionFlowStopsLoadingAndExposesLoadCategory() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val failed =
        flowOf(Result.failure<List<GroupSession>>(EventsException(EventsError.STORAGE_FAILURE)))
    val viewModel = createViewModel(FlowGroupSessionsRepository(failed))

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(GroupSessionsError.LOAD_FAILED, viewModel.uiState.value.error)
  }

  /**
   * Signing out clears sessions, and a different user's flags are recomputed from their identity.
   */
  @Test
  fun authenticationChangesClearAndRecomputeUserRelationships() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val aliceOwn = session("alice-own", ALICE, listOf(ALICE, BOB))
    val bobOwn = session("bob-own", BOB, listOf(BOB), startsAt = now.plusSeconds(120))
    repository.saveSession(ALICE, aliceOwn)
    repository.saveSession(BOB, bobOwn)
    val viewModel = createViewModel()

    assertEquals(
        listOf(true to true, false to false),
        viewModel.uiState.value.sessions.map { it.isMember to it.isCreator },
    )
    authRepository.currentUser.value = null
    assertTrue(viewModel.uiState.value.sessions.isEmpty())
    authRepository.currentUser.value = account(BOB)
    assertEquals(
        listOf(true to false, true to true),
        viewModel.uiState.value.sessions.map { it.isMember to it.isCreator },
    )
  }

  /** Join and leave while signed out are safe no-ops and do not alter the repository. */
  @Test
  fun signedOutJoinAndLeaveDoNotChangeRepositoryOrCrash() = runTest {
    val own = session("own", ALICE)
    repository.saveSession(ALICE, own)
    val viewModel = createViewModel()

    viewModel.join(own.id)
    viewModel.leave(own.id)

    assertEquals(Result.success(own), repository.getSession(own.id))
    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.sessions.isEmpty())
  }

  /**
   * Existing flows retain their initial cutoff; refresh observes again using the advanced clock.
   */
  @Test
  fun refreshRemovesSessionThatEndedSinceObservationStarted() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val mutableClock = MutableTestClock(now)
    val expiring =
        session(
            "expiring",
            ALICE,
            startsAt = now.plusSeconds(60),
            endsAt = now.plusSeconds(120),
        )
    repository.saveSession(ALICE, expiring)
    val viewModel = GroupSessionsViewModel(authRepository, repository, mutableClock)

    assertEquals(listOf("expiring"), visibleIds(viewModel))
    mutableClock.currentInstant = expiring.endsAt
    assertEquals(listOf("expiring"), visibleIds(viewModel))

    viewModel.refresh()

    assertTrue(viewModel.uiState.value.sessions.isEmpty())
  }

  private fun createViewModel(
      sessionsRepository: GroupSessionsRepository = repository,
  ): GroupSessionsViewModel =
      GroupSessionsViewModel(
          authRepository = authRepository,
          repository = sessionsRepository,
          clock = MutableTestClock(now),
      )

  private fun visibleIds(viewModel: GroupSessionsViewModel): List<String> =
      viewModel.uiState.value.sessions.map { it.session.id }

  private fun account(uid: String) = UserAccount(uid, "$uid@example.com", uid, null)

  private fun session(
      id: String,
      creator: String,
      members: List<String> = listOf(creator),
      subject: String? = "Biology",
      location: String? = null,
      description: String? = null,
      type: EventType = if (subject == null) EventType.OTHER else EventType.COURSE,
      startsAt: Instant = now.plusSeconds(60),
      endsAt: Instant = startsAt.plusSeconds(600),
  ) =
      GroupSession(
          id = id,
          createdBy = creator,
          memberIds = members,
          type = type,
          subject = subject,
          startsAt = startsAt,
          endsAt = endsAt,
          createdAt = now,
          location = location,
          description = description,
      )

  private class MutableTestClock(var currentInstant: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock =
        require(zone == ZoneOffset.UTC) { "Test clock only supports UTC" }.let { this }

    override fun instant(): Instant = currentInstant
  }

  private class FlowGroupSessionsRepository(
      private val sessionsFlow: Flow<Result<List<GroupSession>>>
  ) : GroupSessionsRepository {
    override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> =
        sessionsFlow

    override fun newSessionId(): String = "unused"

    override suspend fun getSession(sessionId: String): Result<GroupSession> =
        Result.failure(UnsupportedOperationException("Not used in this test"))

    override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> =
        Result.success(Unit)

    override suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun joinSession(userId: String, sessionId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun leaveSession(userId: String, sessionId: String): Result<Unit> =
        Result.success(Unit)
  }

  private companion object {
    const val ALICE = "alice"
    const val BOB = "bob"
  }
}
