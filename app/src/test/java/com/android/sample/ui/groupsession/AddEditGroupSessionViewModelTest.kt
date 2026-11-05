// Co-authored-by: Claude AI Agent
package com.android.sample.ui.groupsession

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.GroupSessionsRepositoryProvider
import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import com.android.sample.ui.auth.TestAuthRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AddEditGroupSessionViewModelTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val testDispatcher = UnconfinedTestDispatcher()
  private val fixedInstant = Instant.parse("2026-11-01T10:00:00Z")
  private val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

  private lateinit var authRepository: TestAuthRepository
  private lateinit var repository: InMemoryGroupSessionsRepository
  private lateinit var viewModel: AddEditGroupSessionViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = TestAuthRepository()
    authRepository.currentUser.value = UserAccount("user_123", "test@epfl.ch", "Test User", null)
    repository = InMemoryGroupSessionsRepository()
    viewModel = AddEditGroupSessionViewModel(authRepository, repository, clock)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    AuthRepositoryProvider.setRepository(null)
    GroupSessionsRepositoryProvider.setRepository(null)
  }

  @Test
  fun initialUiStateIsDefault() {
    val state = viewModel.uiState.value
    assertEquals(EventType.STUDY_SESSION, state.type)
    assertEquals("", state.subject)
    assertNull(state.date)
    assertNull(state.startTime)
    assertNull(state.endTime)
    assertEquals("", state.location)
    assertEquals("", state.description)
    assertFalse(state.isEditing)
    assertFalse(state.isSaving)
    assertFalse(state.isSessionSaved)
    assertNull(state.error)
    assertTrue(state.isSubjectRequired)
    assertFalse(state.isBusy)
  }

  @Test
  fun formFieldUpdatesChangeUiState() {
    viewModel.setType(EventType.COURSE)
    viewModel.setSubject("Software Engineering")
    viewModel.setDate(LocalDate.of(2026, 11, 15))
    viewModel.setStartTime(LocalTime.of(10, 0))
    viewModel.setEndTime(LocalTime.of(12, 0))
    viewModel.setLocation("BC 01")
    viewModel.setDescription("Group study prep")

    val state = viewModel.uiState.value
    assertEquals(EventType.COURSE, state.type)
    assertEquals("Software Engineering", state.subject)
    assertEquals(LocalDate.of(2026, 11, 15), state.date)
    assertEquals(LocalTime.of(10, 0), state.startTime)
    assertEquals(LocalTime.of(12, 0), state.endTime)
    assertEquals("BC 01", state.location)
    assertEquals("Group study prep", state.description)
  }

  @Test
  fun loadSessionPopulatesStateInEditMode() = runTest {
    val session =
        GroupSession(
            id = "sess_1",
            createdBy = "user_123",
            memberIds = listOf("user_123"),
            details =
                EventDetails(
                    type = EventType.COURSE,
                    subject = "Algorithms",
                    startsAt = Instant.parse("2026-11-15T10:00:00Z"),
                    endsAt = Instant.parse("2026-11-15T12:00:00Z"),
                    location = "INM 202",
                    description = "Problem set 3",
                ),
            createdAt = fixedInstant,
        )
    repository.saveSession("user_123", session)

    viewModel.loadSession("sess_1")

    val state = viewModel.uiState.value
    assertTrue(state.isEditing)
    assertEquals(EventType.COURSE, state.type)
    assertEquals("Algorithms", state.subject)
    assertEquals(LocalDate.of(2026, 11, 15), state.date)
    assertEquals(LocalTime.of(10, 0), state.startTime)
    assertEquals(LocalTime.of(12, 0), state.endTime)
    assertEquals("INM 202", state.location)
    assertEquals("Problem set 3", state.description)
    assertNull(state.error)
  }

  @Test
  fun loadSessionFailureResetsStaleEditState() = runTest {
    val session =
        GroupSession(
            id = "sess_1",
            createdBy = "user_123",
            memberIds = listOf("user_123"),
            details =
                EventDetails(
                    type = EventType.COURSE,
                    subject = "Algorithms",
                    startsAt = Instant.parse("2026-11-15T10:00:00Z"),
                    endsAt = Instant.parse("2026-11-15T12:00:00Z"),
                ),
            createdAt = fixedInstant,
        )
    repository.saveSession("user_123", session)

    viewModel.loadSession("sess_1")
    assertTrue(viewModel.uiState.value.isEditing)

    // Load missing session -> resets state
    viewModel.loadSession("missing_id")

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertFalse(state.isEditing)
    assertEquals(EventType.STUDY_SESSION, state.type)
    assertEquals("", state.subject)
    assertEquals(AddEditGroupSessionError.LOAD_FAILED, state.error)
  }

  @Test
  fun saveSessionFailsWhenDateOrTimesMissing() {
    viewModel.setSubject("Physics")
    viewModel.saveSession()

    assertEquals(
        AddEditGroupSessionError.DATE_AND_TIMES_REQUIRED,
        viewModel.uiState.value.error,
    )
  }

  @Test
  fun saveSessionFailsWhenSubjectMissingAndRequired() {
    viewModel.setType(EventType.COURSE)
    viewModel.setDate(LocalDate.of(2026, 11, 15))
    viewModel.setStartTime(LocalTime.of(10, 0))
    viewModel.setEndTime(LocalTime.of(12, 0))
    viewModel.setSubject("   ")

    viewModel.saveSession()

    assertEquals(
        AddEditGroupSessionError.INVALID_SESSION,
        viewModel.uiState.value.error,
    )
  }

  @Test
  fun saveSessionFailsWhenDurationIsZero() {
    viewModel.setType(EventType.OTHER)
    viewModel.setDate(LocalDate.of(2026, 11, 15))
    viewModel.setStartTime(LocalTime.of(10, 0))
    viewModel.setEndTime(LocalTime.of(10, 0))

    viewModel.saveSession()

    assertEquals(
        AddEditGroupSessionError.INVALID_SESSION,
        viewModel.uiState.value.error,
    )
  }

  @Test
  fun saveSessionFailsWhenDurationExceedsMaxLimit() {
    // When a time zone undergoes daylight saving fall-back (repeating 1 hour),
    // a session from 01:30 to 01:00 next day spans 24.5 hours in UTC.
    // This exercises the ViewModel's Duration > MAX_DURATION validation check.
    val dstClock = Clock.fixed(fixedInstant, ZoneId.of("Europe/Paris"))
    val dstViewModel = AddEditGroupSessionViewModel(authRepository, repository, dstClock)

    dstViewModel.setType(EventType.OTHER)
    dstViewModel.setDate(LocalDate.of(2026, 10, 25))
    dstViewModel.setStartTime(LocalTime.of(1, 30))
    dstViewModel.setEndTime(LocalTime.of(1, 0)) // VieModel adds 24h if endTime < startTime

    dstViewModel.saveSession()

    assertEquals(
        AddEditGroupSessionError.INVALID_SESSION,
        dstViewModel.uiState.value.error,
    )
  }

  @Test
  fun saveSessionSucceedsInAddMode() = runTest {
    viewModel.setType(EventType.STUDY_SESSION)
    viewModel.setSubject("Stochastic Models")
    viewModel.setDate(LocalDate.of(2026, 11, 15))
    viewModel.setStartTime(LocalTime.of(14, 0))
    viewModel.setEndTime(LocalTime.of(16, 0))
    viewModel.setLocation("CM 3")
    viewModel.setDescription("Chapter 5 prep")

    viewModel.saveSession()

    val state = viewModel.uiState.value
    assertTrue(state.isSessionSaved)
    assertNull(state.error)

    val resultList = repository.observeSessions(Instant.EPOCH).first().getOrNull()
    assertNotNull(resultList)
    assertEquals(1, resultList?.size)

    val saved = resultList!!.first()
    assertEquals("user_123", saved.createdBy)
    assertEquals(listOf("user_123"), saved.memberIds)
    assertEquals(EventType.STUDY_SESSION, saved.details.type)
    assertEquals("Stochastic Models", saved.details.subject)
    assertEquals("CM 3", saved.details.location)
    assertEquals("Chapter 5 prep", saved.details.description)

    viewModel.onSessionSavedHandled()
    assertFalse(viewModel.uiState.value.isSessionSaved)
  }

  @Test
  fun saveSessionUpdatesExistingSessionInEditMode() = runTest {
    val initial =
        GroupSession(
            id = "sess_99",
            createdBy = "user_123",
            memberIds = listOf("user_123", "user_456"),
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Initial Subject",
                    startsAt = Instant.parse("2026-11-15T10:00:00Z"),
                    endsAt = Instant.parse("2026-11-15T12:00:00Z"),
                ),
            createdAt = fixedInstant,
        )
    repository.saveSession("user_123", initial)

    viewModel.loadSession("sess_99")
    viewModel.setSubject("Updated Subject")
    viewModel.setLocation("New Room")

    viewModel.saveSession()

    val state = viewModel.uiState.value
    assertTrue(state.isSessionSaved)

    val updated = repository.getSession("sess_99").getOrNull()
    assertNotNull(updated)
    assertEquals("Updated Subject", updated?.details?.subject)
    assertEquals("New Room", updated?.details?.location)
  }

  @Test
  fun saveSessionFailsWhenUnauthenticated() = runTest {
    authRepository.currentUser.value = null

    viewModel.setType(EventType.OTHER)
    viewModel.setDate(LocalDate.of(2026, 11, 15))
    viewModel.setStartTime(LocalTime.of(10, 0))
    viewModel.setEndTime(LocalTime.of(12, 0))

    viewModel.saveSession()

    val state = viewModel.uiState.value
    assertFalse(state.isSaving)
    assertEquals(AddEditGroupSessionError.SAVE_FAILED, state.error)
  }

  @Test
  fun crossMidnightSessionCalculatesCorrectEndInstant() = runTest {
    viewModel.setType(EventType.OTHER)
    viewModel.setDate(LocalDate.of(2026, 11, 15))
    viewModel.setStartTime(LocalTime.of(23, 0))
    viewModel.setEndTime(LocalTime.of(1, 0))

    viewModel.saveSession()

    val state = viewModel.uiState.value
    assertTrue(state.isSessionSaved)

    val resultList = repository.observeSessions(Instant.EPOCH).first().getOrNull()
    assertNotNull(resultList)
    val saved = resultList!!.first()

    // Start at 23:00 on 2026-11-15 UTC -> 2026-11-15T23:00:00Z
    // End at 01:00 next day UTC -> 2026-11-16T01:00:00Z (2 hours later)
    assertEquals(Instant.parse("2026-11-15T23:00:00Z"), saved.details.startsAt)
    assertEquals(Instant.parse("2026-11-16T01:00:00Z"), saved.details.endsAt)
  }

  @Test
  fun whitespaceNormalizationTrimsInputs() = runTest {
    viewModel.setType(EventType.COURSE)
    viewModel.setSubject("   Computer Graphics   ")
    viewModel.setDate(LocalDate.of(2026, 11, 15))
    viewModel.setStartTime(LocalTime.of(10, 0))
    viewModel.setEndTime(LocalTime.of(12, 0))
    viewModel.setLocation("   BC 02   ")
    viewModel.setDescription("   Lab 1   ")

    viewModel.saveSession()

    val resultList = repository.observeSessions(Instant.EPOCH).first().getOrNull()
    assertNotNull(resultList)
    val saved = resultList!!.first()

    assertEquals("Computer Graphics", saved.details.subject)
    assertEquals("BC 02", saved.details.location)
    assertEquals("Lab 1", saved.details.description)
  }

  @Test
  fun saveSessionCatchesIllegalArgumentExceptionFromConstructor() = runTest {
    // If the repository produces an invalid blank ID, GroupSession's init require() throws
    // IllegalArgumentException
    val invalidIdRepo =
        object : GroupSessionsRepository by repository {
          override fun newSessionId(): String = "   "
        }
    val testViewModel = AddEditGroupSessionViewModel(authRepository, invalidIdRepo, clock)
    testViewModel.setType(EventType.OTHER)
    testViewModel.setDate(LocalDate.of(2026, 11, 15))
    testViewModel.setStartTime(LocalTime.of(10, 0))
    testViewModel.setEndTime(LocalTime.of(12, 0))

    testViewModel.saveSession()

    val state = testViewModel.uiState.value
    assertFalse(state.isSaving)
    assertEquals(AddEditGroupSessionError.INVALID_SESSION, state.error)
  }

  @Test
  fun loadSessionWithBlankIdDoesNothing() {
    viewModel.loadSession("   ")
    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertFalse(state.isEditing)
  }

  @Test
  fun saveSessionFailsWhenRepositorySaveReturnsFailure() = runTest {
    val failingRepo =
        object : GroupSessionsRepository by repository {
          override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> =
              Result.failure(RuntimeException("Firestore persistence failure"))
        }
    val failingViewModel = AddEditGroupSessionViewModel(authRepository, failingRepo, clock)
    failingViewModel.setType(EventType.OTHER)
    failingViewModel.setDate(LocalDate.of(2026, 11, 15))
    failingViewModel.setStartTime(LocalTime.of(10, 0))
    failingViewModel.setEndTime(LocalTime.of(12, 0))

    failingViewModel.saveSession()

    val state = failingViewModel.uiState.value
    assertFalse(state.isSaving)
    assertEquals(AddEditGroupSessionError.SAVE_FAILED, state.error)
  }

  @Test
  fun saveSessionWhileBusyDoesNothing() = runTest {
    // Hang saveSession in progress using an unresolved CompletableDeferred in the repository
    val saveStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
    val saveHang = kotlinx.coroutines.CompletableDeferred<Unit>()
    val hangingRepo =
        object : GroupSessionsRepository by repository {
          override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> {
            saveStarted.complete(Unit)
            saveHang.await()
            return Result.success(Unit)
          }
        }
    val hangingViewModel = AddEditGroupSessionViewModel(authRepository, hangingRepo, clock)
    hangingViewModel.setType(EventType.OTHER)
    hangingViewModel.setDate(LocalDate.of(2026, 11, 15))
    hangingViewModel.setStartTime(LocalTime.of(10, 0))
    hangingViewModel.setEndTime(LocalTime.of(12, 0))

    // Start first save session which hangs
    hangingViewModel.saveSession()
    saveStarted.await()
    assertTrue(hangingViewModel.uiState.value.isBusy)

    // Second call while busy should return early without modifying state
    hangingViewModel.saveSession()
    assertTrue(hangingViewModel.uiState.value.isBusy)

    // Complete the first save
    saveHang.complete(Unit)
    assertFalse(hangingViewModel.uiState.value.isBusy)
    assertTrue(hangingViewModel.uiState.value.isSessionSaved)
  }

  @Test
  fun factoryCreatesViewModelOrThrows() {
    val factory = AddEditGroupSessionViewModelFactory(authRepository, repository)
    val instance = factory.create(AddEditGroupSessionViewModel::class.java)
    assertNotNull(instance)

    assertThrows(IllegalArgumentException::class.java) {
      factory.create(GroupSessionsViewModel::class.java)
    }
  }

  /**
   * Fake screen composable that receives the ViewModel produced by [addEditGroupSessionViewModel].
   */
  @Composable
  private fun FakeAddEditGroupSessionScreen(
      viewModel: AddEditGroupSessionViewModel = addEditGroupSessionViewModel()
  ) {
    val uiState by viewModel.uiState.collectAsState()
    Text(
        text = "Type: ${uiState.type.name}, Busy: ${uiState.isBusy}",
        modifier = Modifier.testTag("fake_screen_text"),
    )
  }

  @Test
  fun addEditGroupSessionViewModelInjectsProvidersAndInstantiatesFakeScreen() {
    AuthRepositoryProvider.setRepository(authRepository)
    GroupSessionsRepositoryProvider.setRepository(repository)

    composeTestRule.setContent { FakeAddEditGroupSessionScreen() }

    composeTestRule
        .onNodeWithTag("fake_screen_text")
        .assertTextEquals("Type: STUDY_SESSION, Busy: false")
  }
}
