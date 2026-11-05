// Co-authored-by: Claude AI Agent
package com.android.sample.ui.groupsession

import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventType
import com.android.sample.model.groupsession.GroupSession
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditGroupSessionViewModelTest {

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
            type = EventType.COURSE,
            subject = "Algorithms",
            startsAt = Instant.parse("2026-11-15T10:00:00Z"),
            endsAt = Instant.parse("2026-11-15T12:00:00Z"),
            createdAt = fixedInstant,
            location = "INM 202",
            description = "Problem set 3",
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
            type = EventType.COURSE,
            subject = "Algorithms",
            startsAt = Instant.parse("2026-11-15T10:00:00Z"),
            endsAt = Instant.parse("2026-11-15T12:00:00Z"),
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
    assertEquals(AddEditGroupSessionViewModel.FAILED_TO_SAVE_SESSION_ERROR, state.error)
  }

  @Test
  fun saveSessionFailsWhenDateOrTimesMissing() {
    viewModel.setSubject("Physics")
    viewModel.saveSession()

    assertEquals(
        AddEditGroupSessionViewModel.SESSION_DATE_AND_TIMES_REQUIRED_ERROR,
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
        AddEditGroupSessionViewModel.INVALID_SESSION_ERROR,
        viewModel.uiState.value.error,
    )
  }

  @Test
  fun saveSessionFailsWhenDurationExceedsMaxLimit() {
    viewModel.setType(EventType.OTHER)
    viewModel.setDate(LocalDate.of(2026, 11, 15))
    viewModel.setStartTime(LocalTime.of(10, 0))
    // Duration > 24 hours
    viewModel.setEndTime(LocalTime.of(10, 1))

    // Same start and end time = 0 duration -> invalid
    viewModel.setStartTime(LocalTime.of(10, 0))
    viewModel.setEndTime(LocalTime.of(10, 0))

    viewModel.saveSession()

    assertEquals(
        AddEditGroupSessionViewModel.INVALID_SESSION_ERROR,
        viewModel.uiState.value.error,
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
    assertEquals(EventType.STUDY_SESSION, saved.type)
    assertEquals("Stochastic Models", saved.subject)
    assertEquals("CM 3", saved.location)
    assertEquals("Chapter 5 prep", saved.description)

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
            type = EventType.STUDY_SESSION,
            subject = "Initial Subject",
            startsAt = Instant.parse("2026-11-15T10:00:00Z"),
            endsAt = Instant.parse("2026-11-15T12:00:00Z"),
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
    assertEquals("Updated Subject", updated?.subject)
    assertEquals("New Room", updated?.location)
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
    assertEquals(AddEditGroupSessionViewModel.FAILED_TO_SAVE_SESSION_ERROR, state.error)
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
    assertEquals(Instant.parse("2026-11-15T23:00:00Z"), saved.startsAt)
    assertEquals(Instant.parse("2026-11-16T01:00:00Z"), saved.endsAt)
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

    assertEquals("Computer Graphics", saved.subject)
    assertEquals("BC 02", saved.location)
    assertEquals("Lab 1", saved.description)
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
}
