// Co-authored-by: GitHub Copilot
package com.android.sample.ui.auth

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.auth.AuthError
import com.android.sample.model.auth.AuthException
import com.android.sample.model.auth.UserAccount
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignInRouteTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val viewModelStore = ViewModelStore()
  private lateinit var viewModel: AuthViewModel
  private lateinit var repository: TestAuthRepository
  private val receivedActivities = mutableListOf<Activity>()
  private var signedInCount = 0

  @After
  fun tearDown() {
    viewModelStore.clear()
  }

  @Test
  fun passesCurrentActivityToPickerAndForwardsReturnedTokenToRepository() {
    setRoute()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf(composeTestRule.activity), receivedActivities)
    assertEquals(listOf("fake-token"), repository.tokens)
  }

  @Test
  fun successfulSignInCallsOnSignedInExactlyOnce() {
    setRoute()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, signedInCount)
    composeTestRule.waitForIdle()
    assertEquals(1, signedInCount)
  }

  @Test
  fun alreadySignedInUserCallsOnSignedInExactlyOnceWhenRouteAppears() {
    repositoryForTest().currentUser.value =
        UserAccount("test-user", "test@example.com", "Test User", null)

    setRoute()

    assertEquals(1, signedInCount)
    composeTestRule.waitForIdle()
    assertEquals(1, signedInCount)
  }

  @Test
  fun showsLoadingDisablesButtonAndIgnoresAnotherClickUntilSignInCompletes() {
    val completion = CompletableDeferred<UserAccount>()
    repositoryForTest().signInAction = { completion.await() }
    setRoute()

    val button = composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON)
    button.performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADING).assertIsDisplayed()
    button.assertIsNotEnabled().performClick()
    composeTestRule.waitForIdle()
    assertEquals(listOf(composeTestRule.activity), receivedActivities)
    assertEquals(listOf("fake-token"), repository.tokens)

    completion.complete(UserAccount("test-user", null, null, null))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADING).assertDoesNotExist()
    button.assertIsEnabled()
  }

  @Test
  fun repositoryNetworkFailureShowsLocalizedMessageAndReenablesButton() {
    repositoryForTest().signInAction = { throw AuthException(AuthError.NETWORK) }
    setRoute()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).performClick()
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.ERROR)
        .assertIsDisplayed()
        .assertTextEquals(composeTestRule.activity.getString(R.string.sign_in_error_network))
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertIsEnabled()
    assertEquals(0, signedInCount)
    verifyCallbackRespondsToLaterSignedInUser()
  }

  @Test
  fun pickerWithoutCredentialShowsLocalizedNoCredentialMessage() {
    setRoute(
        getIdToken = { activity ->
          receivedActivities += activity
          throw AuthException(AuthError.NO_CREDENTIAL)
        }
    )

    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf(composeTestRule.activity), receivedActivities)
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.ERROR)
        .assertIsDisplayed()
        .assertTextEquals(composeTestRule.activity.getString(R.string.sign_in_error_no_credential))
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertIsEnabled()
    assertEquals(emptyList<String>(), repository.tokens)
  }

  @Test
  fun dismissedPickerShowsNoErrorAndReenablesButton() {
    setRoute(
        getIdToken = { activity ->
          receivedActivities += activity
          throw AuthException(AuthError.CANCELLED)
        }
    )

    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf(composeTestRule.activity), receivedActivities)
    composeTestRule.onNodeWithTag(SignInScreenTestTags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertIsEnabled()
    assertEquals(emptyList<String>(), repository.tokens)
    assertEquals(0, signedInCount)
    verifyCallbackRespondsToLaterSignedInUser()
  }

  @Test
  fun retryClearsPreviousErrorAsNewSignInStarts() {
    var attempt = 0
    val retryCompletion = CompletableDeferred<UserAccount>()
    repositoryForTest().signInAction = {
      attempt++
      if (attempt == 1) throw AuthException(AuthError.NETWORK)
      retryCompletion.await()
    }
    setRoute()
    val button = composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON)

    button.performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.ERROR).assertIsDisplayed()

    button.performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADING).assertIsDisplayed()
    assertEquals(listOf("fake-token", "fake-token"), repository.tokens)

    retryCompletion.complete(UserAccount("test-user", null, null, null))
    composeTestRule.waitForIdle()
  }

  private fun repositoryForTest(): TestAuthRepository {
    if (!::repository.isInitialized) repository = TestAuthRepository()
    return repository
  }

  private fun verifyCallbackRespondsToLaterSignedInUser() {
    repository.currentUser.value = UserAccount("test-user", null, null, null)
    composeTestRule.waitForIdle()
    assertEquals(1, signedInCount)
  }

  private fun setRoute(
      getIdToken: suspend (Activity) -> String = { activity ->
        receivedActivities += activity
        "fake-token"
      }
  ) {
    val testRepository = repositoryForTest()
    viewModel = AuthViewModel(testRepository)
    viewModelStore.put("auth", viewModel)
    composeTestRule.setContent {
      SignInRoute(
          viewModel = viewModel,
          onSignedIn = { signedInCount++ },
          getIdToken = getIdToken,
      )
    }
    composeTestRule.waitForIdle()
  }
}
