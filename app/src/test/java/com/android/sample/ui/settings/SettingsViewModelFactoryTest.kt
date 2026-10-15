// Co-authored-by: Claude AI Agent
package com.android.sample.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.ProfileRepositoryProvider
import com.android.sample.model.profile.ProfileResolution
import com.android.sample.model.profile.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [SettingsViewModelFactory] and the [settingsViewModel] composable that uses it.
 *
 * The providers' repositories are replaced with fakes, so Firebase and Firestore are never
 * initialized. They are cleared after each test because the Robolectric sandbox shares static state
 * between tests.
 */
@RunWith(AndroidJUnit4::class)
class SettingsViewModelFactoryTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val authRepository = FakeAuthRepository()
  private val profileRepository = FakeProfileRepository()
  private val factory = SettingsViewModelFactory(authRepository, profileRepository)

  @After
  fun clearProviders() {
    setAuthProviderRepository(null)
    setProfileProviderRepository(null)
  }

  // ---------------------------------------------------------------------------
  // SettingsViewModelFactory
  // ---------------------------------------------------------------------------

  @Test
  fun createReturnsASettingsViewModel() {
    val viewModel = factory.create(SettingsViewModel::class.java)

    assertEquals(SettingsViewModel::class.java, viewModel.javaClass)
  }

  @Test
  fun createRejectsOtherViewModelTypesAndNamesTheOffendingType() {
    val error =
        assertThrows(IllegalArgumentException::class.java) {
          factory.create(OtherViewModel::class.java)
        }

    assertTrue(error.message.orEmpty().contains(OtherViewModel::class.java.name))
  }

  @Test
  fun createdViewModelSignsOutThroughTheInjectedAuthRepository() {
    useEmptyComposition()
    val viewModel = factory.create(SettingsViewModel::class.java)

    viewModel.signOut()

    composeTestRule.waitUntil(timeoutMillis = 5_000) { authRepository.signOutCalls == 1 }
    assertEquals(1, authRepository.signOutCalls)
  }

  @Test
  fun createdViewModelObservesTheInjectedAuthRepository() {
    useEmptyComposition()
    val viewModel = factory.create(SettingsViewModel::class.java)

    // The ViewModel starts in the loading state and only leaves it once the injected repository
    // reports the current user (here: nobody is signed in).
    composeTestRule.waitUntil(timeoutMillis = 5_000) { !viewModel.uiState.value.isLoading }
    assertNull(viewModel.uiState.value.error)
  }

  // ---------------------------------------------------------------------------
  // settingsViewModel()
  // ---------------------------------------------------------------------------

  @Test
  fun settingsViewModelUsesProviderRepositoriesAndSurvivesRecomposition() {
    setAuthProviderRepository(authRepository)
    setProfileProviderRepository(
        FakeProfileRepository(UserProfile(uid = "test-user", username = "Alice"))
    )
    val trigger = mutableStateOf(0)
    val received = mutableListOf<SettingsViewModel>()
    composeTestRule.setContent {
      trigger.value
      received += settingsViewModel()
    }
    composeTestRule.runOnIdle { trigger.value++ }
    composeTestRule.waitForIdle()

    // Recomposing returns the very same ViewModel instead of creating a new one.
    assertTrue(received.size >= 2)
    assertTrue(received.all { it === received.first() })

    // The user comes from the auth provider's repository and the name from the profile
    // provider's repository, so both providers feed the ViewModel.
    authRepository.currentUser.value =
        UserAccount(uid = "test-user", displayName = null, email = null, photoUrl = null)
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      received.first().uiState.value.username == "Alice"
    }
    assertEquals("Alice", received.first().uiState.value.username)
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Starts an (empty) composition so the compose rule can idle the main thread while the
   * ViewModel's coroutines run. That way the tests behave the same on a device and under
   * Robolectric.
   */
  private fun useEmptyComposition() {
    composeTestRule.setContent {}
  }

  private fun setAuthProviderRepository(repository: AuthRepository?) {
    AuthRepositoryProvider::class.java.getDeclaredField("instance").apply {
      isAccessible = true
      set(AuthRepositoryProvider, repository)
    }
  }

  private fun setProfileProviderRepository(repository: ProfileRepository?) {
    ProfileRepositoryProvider::class.java.getDeclaredField("instance").apply {
      isAccessible = true
      set(ProfileRepositoryProvider, repository)
    }
  }

  private class OtherViewModel : ViewModel()

  /** Minimal in-memory repository: nobody is signed in and sign-outs are counted. */
  private class FakeAuthRepository : AuthRepository {
    override val currentUser = MutableStateFlow<UserAccount?>(null)

    var signOutCalls = 0
      private set

    override suspend fun signInWithGoogle(idToken: String): UserAccount =
        throw UnsupportedOperationException("Not needed by these tests")

    override suspend fun signOut() {
      signOutCalls++
    }
  }

  /** Minimal in-memory repository that always returns [profile] (or no profile at all). */
  private class FakeProfileRepository(private val profile: UserProfile? = null) :
      ProfileRepository {
    override suspend fun getProfile(userId: String): Result<UserProfile?> = Result.success(profile)

    override suspend fun createProfile(userId: String): Result<ProfileResolution> =
        Result.success(ProfileResolution.Created(UserProfile(uid = userId)))
  }
}
