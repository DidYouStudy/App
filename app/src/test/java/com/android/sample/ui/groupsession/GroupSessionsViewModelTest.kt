// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.groupsession

import androidx.lifecycle.viewModelScope
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import com.android.sample.model.event.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.UserProfile
import com.android.sample.ui.auth.TestAuthRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
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
  private lateinit var profileRepository: FakeProfileRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = TestAuthRepository()
    repository = InMemoryGroupSessionsRepository()
    profileRepository = FakeProfileRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** A signed-out user gets an empty, non-loading state without an error. */
  @Test
  fun signedOut_startsWithEmptyNonLoadingState() = runTest {
    val viewModel = createViewModel()

    assertEquals(now, viewModel.uiState.value.now)
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

  /** A successful unrelated list update does not clear a join failure. */
  @Test
  fun joinFailure_survivesUnrelatedSessionUpdate() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val viewModel = createViewModel()

    viewModel.join("missing")
    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)

    repository.saveSession(BOB, session("unrelated", BOB))

    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)
  }

  /** A successful unrelated list update does not clear a leave failure. */
  @Test
  fun leaveFailure_survivesUnrelatedSessionUpdate() = runTest {
    authRepository.currentUser.value = account(ALICE)
    repository.saveSession(ALICE, session("own", ALICE))
    val viewModel = createViewModel()

    viewModel.leave("own")
    assertEquals(GroupSessionsError.LEAVE_FAILED, viewModel.uiState.value.error)

    repository.saveSession(BOB, session("unrelated", BOB))

    assertEquals(GroupSessionsError.LEAVE_FAILED, viewModel.uiState.value.error)
  }

  /** Refreshing the list does not clear a join failure. */
  @Test
  fun refresh_preservesJoinFailure() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val viewModel = createViewModel()

    viewModel.join("missing")
    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)

    viewModel.refresh()

    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)
  }

  /** A delayed join failure is ignored after switching directly to another user. */
  @Test
  fun joinFailureFromPreviousAccount_isIgnored() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val delayedRepository = DelayedGroupSessionsRepository()
    val viewModel = createViewModel(delayedRepository)

    viewModel.join("target")
    assertTrue(delayedRepository.joinStarted.isCompleted)
    authRepository.currentUser.value = account(BOB)
    delayedRepository.joinResult.complete(Result.failure(IllegalStateException()))

    assertNull(viewModel.uiState.value.error)
  }

  /** A delayed leave failure does not alter the signed-out state. */
  @Test
  fun leaveFailureAfterSignOut_isIgnored() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val delayedRepository = DelayedGroupSessionsRepository()
    val viewModel = createViewModel(delayedRepository)

    viewModel.leave("target")
    assertTrue(delayedRepository.leaveStarted.isCompleted)
    authRepository.currentUser.value = null
    delayedRepository.leaveResult.complete(Result.failure(IllegalStateException()))

    assertTrue(viewModel.uiState.value.sessions.isEmpty())
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
  }

  /** Signing out and back in as the same user invalidates an earlier join result. */
  @Test
  fun joinFailureAfterSigningOutAndBackIn_isIgnored() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val delayedRepository = DelayedGroupSessionsRepository()
    val viewModel = createViewModel(delayedRepository)

    viewModel.join("target")
    assertTrue(delayedRepository.joinStarted.isCompleted)
    authRepository.currentUser.value = null
    authRepository.currentUser.value = account(ALICE)
    delayedRepository.joinResult.complete(Result.failure(IllegalStateException()))

    assertNull(viewModel.uiState.value.error)
  }

  /** A later successful list emission clears a preceding load failure and displays the list. */
  @Test
  fun successfulEmission_clearsLoadFailure() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    emissions.emit(Result.failure(EventsException(EventsError.STORAGE_FAILURE)))
    assertEquals(GroupSessionsError.LOAD_FAILED, viewModel.uiState.value.error)
    val shown = session("shown", ALICE)

    emissions.emit(Result.success(listOf(shown)))

    assertNull(viewModel.uiState.value.error)
    assertEquals(listOf(shown), viewModel.uiState.value.sessions.map { it.session })
  }

  /** A refresh does not invalidate a still-pending join failure for the same user. */
  @Test
  fun joinFailure_survivesRefreshWhilePending() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val delayedRepository = DelayedGroupSessionsRepository()
    val viewModel = createViewModel(delayedRepository)

    viewModel.join("target")
    assertTrue(delayedRepository.joinStarted.isCompleted)
    viewModel.refresh()
    delayedRepository.joinResult.complete(Result.failure(IllegalStateException()))

    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)
  }

  /** Switching users clears an action error belonging to the previous account. */
  @Test
  fun switchingUsers_clearsActionError() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val viewModel = createViewModel()
    viewModel.join("missing")
    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)

    authRepository.currentUser.value = account(BOB)

    assertNull(viewModel.uiState.value.error)
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

  /** A failure after a successful load keeps the last list and exposes LOAD_FAILED. */
  @Test
  fun loadFailureAfterSuccessKeepsLastListAndShowsError() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val shown = session("shown", ALICE)
    emissions.emit(Result.success(listOf(shown)))

    emissions.emit(Result.failure(EventsException(EventsError.STORAGE_FAILURE)))

    assertEquals(listOf(shown), viewModel.uiState.value.sessions.map { it.session })
    assertEquals(GroupSessionsError.LOAD_FAILED, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)
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
    val viewModel =
        GroupSessionsViewModel(authRepository, repository, profileRepository, mutableClock)

    assertEquals(listOf("expiring"), visibleIds(viewModel))
    mutableClock.currentInstant = expiring.details.endsAt
    assertEquals(listOf("expiring"), visibleIds(viewModel))

    viewModel.refresh()

    assertTrue(viewModel.uiState.value.sessions.isEmpty())
  }

  /** Refresh updates the reference time even when no screen collects the state. */
  @Test
  fun now_refreshUpdatesTimeWithoutUiStateCollector() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val clock = MutableTestClock(now)
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            FlowGroupSessionsRepository(flowOf(Result.success(emptyList()))),
            clock,
        )
    clock.currentInstant = Instant.parse("2025-03-01T12:00:25Z")

    viewModel.refresh()
    runCurrent()

    assertEquals(Instant.parse("2025-03-01T12:00:25Z"), viewModel.uiState.value.now)
  }

  /** Signing in updates the reference time without waiting for a visible screen or tick. */
  @Test
  fun now_signInUpdatesTimeWithoutUiStateCollector() = runTest {
    val clock = MutableTestClock(now)
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            FlowGroupSessionsRepository(flowOf(Result.success(emptyList()))),
            clock,
        )
    clock.currentInstant = Instant.parse("2025-03-01T12:00:25Z")

    authRepository.currentUser.value = account(ALICE)
    runCurrent()

    assertEquals(Instant.parse("2025-03-01T12:00:25Z"), viewModel.uiState.value.now)
  }

  /** The refreshed list cutoff and UI reference time come from the same clock read. */
  @Test
  fun now_refreshUsesSameInstantAsRepositoryCutoff() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        FlowGroupSessionsRepository(flowOf(Result.success(emptyList<GroupSession>())))
    val viewModel =
        GroupSessionsViewModel(authRepository, sessionsRepository, AdvancingTestClock(now))

    viewModel.refresh()
    runCurrent()

    assertEquals(sessionsRepository.endingAfters.last(), viewModel.uiState.value.now)
  }

  /** The first signed-in state carries the injected time before the repository emits. */
  @Test
  fun now_startsFromTheInjectedClock() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            FlowGroupSessionsRepository(emissions),
            profileRepository,
            MutableTestClock(now),
        )

    assertEquals(now, viewModel.uiState.value.now)
    assertTrue(viewModel.uiState.value.isLoading)
  }

  /** The time changes after exactly 30 seconds of being observed, and not one moment earlier. */
  @Test
  fun now_refreshesEveryThirtySecondsWhileObserved() = runTest {
    val clock = MutableTestClock(now)
    val viewModel = GroupSessionsViewModel(authRepository, repository, profileRepository, clock)
    observe(viewModel)
    val later = now.plusSeconds(30)
    clock.currentInstant = later

    advanceTimeBy(29_999)
    runCurrent()
    assertEquals(now, viewModel.uiState.value.now)

    advanceTimeBy(1)
    runCurrent()
    assertEquals(later, viewModel.uiState.value.now)
  }

  /** A time tick updates the clock without clearing an existing join failure. */
  @Test
  fun now_refreshPreservesJoinFailure() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val own = session("own", ALICE)
    repository.saveSession(ALICE, own)
    val clock = MutableTestClock(now)
    val viewModel = GroupSessionsViewModel(authRepository, repository, profileRepository, clock)
    viewModel.join("missing")
    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)
    observe(viewModel)
    clock.currentInstant = Instant.parse("2025-03-01T12:00:30Z")

    advanceTimeBy(30_000)
    runCurrent()

    assertEquals(Instant.parse("2025-03-01T12:00:30Z"), viewModel.uiState.value.now)
    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)
  }

  /** A time tick preserves loading while the signed-in repository has not emitted. */
  @Test
  fun now_refreshPreservesLoadingBeforeFirstEmission() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val clock = MutableTestClock(now)
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            FlowGroupSessionsRepository(emissions),
            profileRepository,
            clock,
        )
    observe(viewModel)
    clock.currentInstant = Instant.parse("2025-03-01T12:00:30Z")

    advanceTimeBy(30_000)
    runCurrent()

    assertEquals(Instant.parse("2025-03-01T12:00:30Z"), viewModel.uiState.value.now)
    assertTrue(viewModel.uiState.value.isLoading)
  }

  /** A hidden screen does no work, and coming back shows the right time without waiting. */
  @Test
  fun now_pausesWithoutCollectorAndUpdatesAsSoonAsOneReturns() = runTest {
    val clock = MutableTestClock(now)
    val viewModel = GroupSessionsViewModel(authRepository, repository, profileRepository, clock)
    val hiddenAt = now.plusSeconds(600)
    observe(viewModel).cancel()
    clock.currentInstant = hiddenAt

    advanceTimeBy(60_000)
    runCurrent()
    val timeWithoutCollector = viewModel.uiState.value.now

    val collector = observe(viewModel)
    val timeAfterReturn = viewModel.uiState.value.now
    collector.cancel()
    // Cancel ViewModel work before assertions so its collectors cannot mutate state afterward.
    viewModel.viewModelScope.cancel()

    assertEquals(now, timeWithoutCollector)
    assertEquals(hiddenAt, timeAfterReturn)
  }

  /** The sign-out reset rebuilds the whole state, which must not bring back an old time. */
  @Test
  fun now_staysCurrentAfterSignOut() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val clock = MutableTestClock(now)
    val viewModel = GroupSessionsViewModel(authRepository, repository, profileRepository, clock)
    val signedOutAt = now.plusSeconds(600)
    clock.currentInstant = signedOutAt

    authRepository.currentUser.value = null

    assertEquals(signedOutAt, viewModel.uiState.value.now)
  }

  /** A refresh only changes the time: the list, search text and filter stay as they were. */
  @Test
  fun now_refreshLeavesTheRestOfTheStateUntouched() = runTest {
    authRepository.currentUser.value = account(ALICE)
    repository.saveSession(ALICE, session("own", ALICE))
    val clock = MutableTestClock(now)
    val viewModel = GroupSessionsViewModel(authRepository, repository, profileRepository, clock)
    viewModel.onQueryChange("bio")
    viewModel.onFilterChange(GroupSessionFilter.JOINED)
    val before = viewModel.uiState.value
    observe(viewModel)
    val later = now.plusSeconds(30)
    clock.currentInstant = later

    advanceTimeBy(30_000)
    runCurrent()

    assertEquals(before.copy(now = later), viewModel.uiState.value)
  }

  /** A host lookup does not delay the list, then supplies the resolved username. */
  @Test
  fun hostNameLookupDoesNotBlockListAndPublishesNameWhenFinished() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val pending = CompletableDeferred<Result<UserProfile?>>()
    profileRepository.hold(BOB, pending)
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val hosted = session("hosted", BOB)

    emissions.emit(Result.success(listOf(hosted)))
    runCurrent()

    assertEquals(listOf(BOB), profileRepository.getProfileUserIds)
    assertEquals(listOf(hosted.id), visibleIds(viewModel))
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.sessions.single().hostName)

    pending.complete(Result.success(UserProfile(uid = BOB, username = "Bob")))
    runCurrent()

    assertEquals("Bob", viewModel.uiState.value.sessions.single().hostName)
  }

  /**
   * Missing, blank and failed profiles stay null without list errors and retry on new emissions.
   */
  @Test
  fun unusableHostProfilesStayNullAndAreRetriedOnNextListUpdate() = runTest {
    authRepository.currentUser.value = account(ALICE)
    profileRepository.enqueue("blank-host", Result.success(UserProfile("blank-host", "   ")))
    profileRepository.enqueue(
        "failed-host",
        Result.failure<UserProfile?>(IllegalStateException("profile lookup failed")),
    )
    profileRepository.enqueue("missing-host", Result.success(null))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val sessions =
        listOf(
            session("blank", "blank-host"),
            session("failed", "failed-host"),
            session("missing", "missing-host"),
        )

    emissions.emit(Result.success(sessions))
    runCurrent()

    assertEquals(sessions.map { it.id }, visibleIds(viewModel))
    assertEquals(
        setOf("blank-host", "failed-host", "missing-host"),
        profileRepository.getProfileUserIds.toSet(),
    )
    assertTrue(viewModel.uiState.value.sessions.all { it.hostName == null })
    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)

    emissions.emit(Result.success(sessions))
    runCurrent()

    assertEquals(2, profileRepository.getProfileUserIds.count { it == "blank-host" })
    assertEquals(2, profileRepository.getProfileUserIds.count { it == "failed-host" })
    assertEquals(2, profileRepository.getProfileUserIds.count { it == "missing-host" })
    assertNull(viewModel.uiState.value.error)
  }

  /** Host lookups deduplicate, skip the signed-in user and survive later list and view updates. */
  @Test
  fun hostNamesAreCachedAcrossHostsFiltersTicksAndListUpdates() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val pendingBob = CompletableDeferred<Result<UserProfile?>>()
    profileRepository.hold(BOB, pendingBob)
    profileRepository.enqueue("carol", Result.success(UserProfile("carol", "Carol")))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val clock = MutableTestClock(now)
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions), clock = clock)
    val own = session("own", ALICE)
    val bobFirst = session("bob-first", BOB, listOf(BOB, ALICE), subject = "Biology")
    val bobSecond = session("bob-second", BOB, subject = "Chemistry")
    val carol = session("carol", "carol", listOf("carol", ALICE))

    emissions.emit(Result.success(listOf(own, bobFirst, bobSecond, carol)))
    runCurrent()

    assertEquals(1, profileRepository.getProfileUserIds.count { it == BOB })
    assertEquals(1, profileRepository.getProfileUserIds.count { it == "carol" })
    assertFalse(profileRepository.getProfileUserIds.contains(ALICE))
    emissions.emit(Result.success(listOf(own, bobFirst, bobSecond, carol)))
    runCurrent()
    assertEquals(1, profileRepository.getProfileUserIds.count { it == BOB })

    pendingBob.complete(Result.success(UserProfile(uid = BOB, username = "Bob")))
    runCurrent()
    assertEquals(
        "Bob",
        viewModel.uiState.value.sessions.first { it.session.id == "bob-first" }.hostName,
    )

    viewModel.onQueryChange("biology")
    runCurrent()
    assertEquals(listOf("bob-first"), visibleIds(viewModel))
    assertEquals("Bob", viewModel.uiState.value.sessions.single().hostName)
    viewModel.onQueryChange("")
    viewModel.onFilterChange(GroupSessionFilter.JOINED)
    runCurrent()
    assertEquals(listOf("Bob", "Carol"), viewModel.uiState.value.sessions.map { it.hostName })

    val collector = observe(viewModel)
    clock.currentInstant = now.plusSeconds(30)
    advanceTimeBy(30_000)
    runCurrent()
    assertEquals(now.plusSeconds(30), viewModel.uiState.value.now)
    assertEquals(listOf("Bob", "Carol"), viewModel.uiState.value.sessions.map { it.hostName })
    collector.cancel()

    val bobLater = session("bob-later", BOB, listOf(BOB, ALICE), subject = "Physics")
    emissions.emit(Result.success(listOf(own, bobFirst, bobSecond, carol, bobLater)))
    runCurrent()
    assertEquals(1, profileRepository.getProfileUserIds.count { it == BOB })
    assertEquals(
        "Bob",
        viewModel.uiState.value.sessions.first { it.session.id == "bob-later" }.hostName,
    )
  }

  /** Refresh repeats profile reads so a changed username replaces the cached value. */
  @Test
  fun refreshReadsHostProfileAgainAndShowsChangedUsername() = runTest {
    authRepository.currentUser.value = account(ALICE)
    profileRepository.enqueue(
        BOB,
        Result.success(UserProfile(uid = BOB, username = "Bob")),
    )
    profileRepository.enqueue(BOB, Result.success(UserProfile(uid = BOB, username = "Robert")))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val hosted = session("hosted", BOB)
    emissions.emit(Result.success(listOf(hosted)))
    runCurrent()
    assertEquals("Bob", viewModel.uiState.value.sessions.single().hostName)

    viewModel.refresh()
    runCurrent()
    emissions.emit(Result.success(listOf(hosted)))
    runCurrent()

    assertEquals("Robert", viewModel.uiState.value.sessions.single().hostName)
  }

  /** A held lookup finishing after sign-out cannot restore or mutate the signed-out list. */
  @Test
  fun lateHostLookupAfterSignOutDoesNotChangeStateOrCrash() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val pending = CompletableDeferred<Result<UserProfile?>>()
    profileRepository.hold(BOB, pending)
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val hosted = session("hosted", BOB)
    val signedOutState = GroupSessionsUiState(now = now, isLoading = false)
    emissions.emit(Result.success(listOf(hosted)))
    runCurrent()
    assertEquals(listOf(BOB), profileRepository.getProfileUserIds)
    assertFalse(pending.isCompleted)
    assertEquals(listOf(hosted.id), visibleIds(viewModel))
    assertNull(viewModel.uiState.value.sessions.single().hostName)

    authRepository.currentUser.value = null
    runCurrent()
    assertEquals(signedOutState, viewModel.uiState.value)

    pending.complete(Result.success(UserProfile(uid = BOB, username = "Bob")))
    runCurrent()

    assertEquals(signedOutState, viewModel.uiState.value)
  }

  /** Collects the state like a visible screen does, until the test ends or the job is cancelled. */
  private fun TestScope.observe(viewModel: GroupSessionsViewModel) =
      backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

  private fun createViewModel(
      sessionsRepository: GroupSessionsRepository = repository,
      profiles: FakeProfileRepository = profileRepository,
      clock: Clock = MutableTestClock(now),
  ): GroupSessionsViewModel =
      GroupSessionsViewModel(
          authRepository = authRepository,
          repository = sessionsRepository,
          profileRepository = profiles,
          clock = clock,
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
          details =
              EventDetails(
                  type = type,
                  subject = subject,
                  startsAt = startsAt,
                  endsAt = endsAt,
                  location = location,
                  description = description,
              ),
          createdAt = now,
      )

  private class MutableTestClock(var currentInstant: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock =
        require(zone == ZoneOffset.UTC) { "Test clock only supports UTC" }.let { this }

    override fun instant(): Instant = currentInstant
  }

  private class AdvancingTestClock(private val firstInstant: Instant) : Clock() {
    private var reads = 0L

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock =
        require(zone == ZoneOffset.UTC) { "Test clock only supports UTC" }.let { this }

    override fun instant(): Instant = firstInstant.plusSeconds(reads++)
  }

  private class FlowGroupSessionsRepository(
      private val sessionsFlow: Flow<Result<List<GroupSession>>>
  ) : GroupSessionsRepository {
    val endingAfters = mutableListOf<Instant>()

    override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> {
      endingAfters.add(endingAfter)
      return sessionsFlow
    }

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

  /** Repository fake that lets tests complete join and leave calls after an auth change. */
  private class DelayedGroupSessionsRepository : GroupSessionsRepository {
    private val sessions = MutableStateFlow<Result<List<GroupSession>>>(Result.success(emptyList()))
    val joinStarted = CompletableDeferred<Unit>()
    val leaveStarted = CompletableDeferred<Unit>()
    val joinResult = CompletableDeferred<Result<Unit>>()
    val leaveResult = CompletableDeferred<Result<Unit>>()

    override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> = sessions

    override fun newSessionId(): String = "unused"

    override suspend fun getSession(sessionId: String): Result<GroupSession> =
        Result.failure(UnsupportedOperationException("Not used in this test"))

    override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> =
        Result.success(Unit)

    override suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun joinSession(userId: String, sessionId: String): Result<Unit> {
      joinStarted.complete(Unit)
      return joinResult.await()
    }

    override suspend fun leaveSession(userId: String, sessionId: String): Result<Unit> {
      leaveStarted.complete(Unit)
      return leaveResult.await()
    }
  }

  private class FakeProfileRepository : ProfileRepository {
    val getProfileUserIds = mutableListOf<String>()
    private val outcomes = mutableMapOf<String, MutableList<Result<UserProfile?>>>()
    private val heldLookups = mutableMapOf<String, CompletableDeferred<Result<UserProfile?>>>()

    fun enqueue(userId: String, result: Result<UserProfile?>) {
      outcomes.getOrPut(userId) { mutableListOf() }.add(result)
    }

    fun hold(userId: String, result: CompletableDeferred<Result<UserProfile?>>) {
      heldLookups[userId] = result
    }

    override suspend fun getProfile(userId: String): Result<UserProfile?> {
      getProfileUserIds += userId
      heldLookups.remove(userId)?.let { pending ->
        return withContext(NonCancellable) { pending.await() }
      }
      val configured = outcomes[userId]
      return if (configured.isNullOrEmpty()) Result.success(null) else configured.removeAt(0)
    }

    override suspend fun createProfile(userId: String): Result<UserProfile> =
        Result.success(UserProfile(uid = userId))
  }

  private companion object {
    const val ALICE = "alice"
    const val BOB = "bob"
  }
}
