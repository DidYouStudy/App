// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.ui.preferences

import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.InMemoryEventsRepository
import com.android.sample.model.event.ScheduleImporter
import com.android.sample.ui.auth.TestAuthRepository
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleImportViewModelTest {

  private lateinit var auth: TestAuthRepository
  private lateinit var viewModel: ScheduleImportViewModel

  private val validIcs =
      """
      BEGIN:VCALENDAR
      BEGIN:VEVENT
      UID:a@x
      SUMMARY:Algebra
      CATEGORIES:Cours
      DTSTART;TZID=Europe/Zurich:20261015T080000
      DURATION:PT2H
      END:VEVENT
      END:VCALENDAR
      """
          .trimIndent()

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    auth = TestAuthRepository()
    auth.currentUser.value = UserAccount("user-1", null, null, null)
    viewModel = ScheduleImportViewModel(ScheduleImporter(InMemoryEventsRepository()), auth)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialState_isIdle() {
    assertEquals(ScheduleImportState.Idle, viewModel.state.value)
  }

  @Test
  fun importSchedule_validFile_reportsCount() = runTest {
    viewModel.importSchedule { ByteArrayInputStream(validIcs.toByteArray()) }

    awaitSettled()
    assertEquals(ScheduleImportState.Success(1), viewModel.state.value)
  }

  @Test
  fun importSchedule_invalidFile_reportsFailure() = runTest {
    viewModel.importSchedule { ByteArrayInputStream("nope".toByteArray()) }

    awaitSettled()
    assertEquals(ScheduleImportState.Failure, viewModel.state.value)
  }

  @Test
  fun importSchedule_signedOut_reportsFailure() = runTest {
    auth.currentUser.value = null

    viewModel.importSchedule { ByteArrayInputStream(validIcs.toByteArray()) }

    awaitSettled()
    assertEquals(ScheduleImportState.Failure, viewModel.state.value)
  }

  @Test
  fun consumeResult_returnsToIdle() = runTest {
    viewModel.importSchedule { ByteArrayInputStream(validIcs.toByteArray()) }
    awaitSettled()

    viewModel.consumeResult()

    assertEquals(ScheduleImportState.Idle, viewModel.state.value)
  }

  /** The importer reads on Dispatchers.IO, so wait (bounded) until the import leaves Importing. */
  private fun awaitSettled() {
    val deadline = System.currentTimeMillis() + 5_000
    while (
        viewModel.state.value == ScheduleImportState.Importing &&
            System.currentTimeMillis() < deadline
    ) {
      Thread.sleep(10)
    }
  }
}
