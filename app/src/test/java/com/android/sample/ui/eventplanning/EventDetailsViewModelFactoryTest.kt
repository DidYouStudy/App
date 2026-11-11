// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.eventplanning

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.event.EventsRepositoryProvider
import com.android.sample.model.event.InMemoryEventsRepository
import com.android.sample.ui.auth.TestAuthRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests injected factory dependencies and provider-backed Compose ViewModel ownership. */
@RunWith(AndroidJUnit4::class)
class EventDetailsViewModelFactoryTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val authRepository = TestAuthRepository()
  private val eventsRepository = InMemoryEventsRepository()
  private val factory = EventDetailsViewModelFactory(authRepository, eventsRepository)

  @After
  fun clearProviders() {
    AuthRepositoryProvider.setRepository(null)
    EventsRepositoryProvider.setRepository(null)
  }

  @Test
  fun createReturnsAnEventDetailsViewModel() {
    val viewModel = factory.create(EventDetailsViewModel::class.java)
    assertEquals(EventDetailsViewModel::class.java, viewModel.javaClass)
  }

  @Test
  fun createRejectsAnotherViewModelTypeAndNamesIt() {
    val error =
        assertThrows(IllegalArgumentException::class.java) {
          factory.create(OtherViewModel::class.java)
        }
    assertTrue(error.message.orEmpty().contains(OtherViewModel::class.java.name))
  }

  @Test
  fun eventDetailsViewModelUsesProvidersAndSurvivesRecomposition() {
    AuthRepositoryProvider.setRepository(authRepository)
    EventsRepositoryProvider.setRepository(eventsRepository)

    val trigger = mutableStateOf(0)
    val received = mutableListOf<EventDetailsViewModel>()

    composeTestRule.setContent {
      trigger.value
      received += eventDetailsViewModel()
    }
    composeTestRule.runOnIdle { trigger.value++ }
    composeTestRule.waitForIdle()

    assertTrue(received.size >= 2)
    assertTrue(received.all { it === received.first() })
  }

  private class OtherViewModel : ViewModel()
}
