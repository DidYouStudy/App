// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Codex AI Agent
package com.android.sample.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.ProfileResolution
import com.android.sample.model.profile.ProfileResolutionCoordinator
import com.android.sample.model.profile.UserProfile
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The provider's repository is replaced with a fake, so Firebase is never initialized. It is
 * cleared around each test because the Robolectric sandbox shares static state between tests.
 */
@RunWith(AndroidJUnit4::class)
class AuthViewModelFactoryTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val repository = TestAuthRepository()

  @Before fun installFake() = setProviderRepository(repository)

  @After fun clearFake() = setProviderRepository(null)

  @Test
  fun factoryCreatesAuthViewModelAndRejectsOtherTypes() {
    val factory = AuthViewModelFactory(repository, testProfileResolutionCoordinator())

    assertTrue(factory.create(AuthViewModel::class.java) is AuthViewModel)
    assertThrows(IllegalArgumentException::class.java) {
      factory.create(OtherViewModel::class.java)
    }
  }

  @Test
  fun authViewModelUsesProviderRepositoryAndSurvivesRecomposition() {
    val trigger = mutableStateOf(0)
    val received = mutableListOf<AuthViewModel>()
    composeTestRule.setContent {
      trigger.value
      received +=
          authViewModel(AuthViewModelFactory(repository, testProfileResolutionCoordinator()))
    }
    composeTestRule.runOnIdle { trigger.value++ }
    composeTestRule.waitForIdle()

    assertTrue(received.size >= 2)
    assertTrue(received.all { it === received.first() })
    val user = UserAccount("test-user", null, null, null)
    repository.currentUser.value = user
    composeTestRule.waitForIdle()
    assertEquals(user, received.first().authUiState.value.user)
  }

  private class OtherViewModel : ViewModel()

  private fun testProfileResolutionCoordinator() =
      ProfileResolutionCoordinator(
          object : ProfileRepository {
            override suspend fun getProfile(userId: String): Result<UserProfile?> =
                Result.success(null)

            override suspend fun createProfile(userId: String): Result<ProfileResolution> =
                Result.success(ProfileResolution.Created(UserProfile(userId)))
          }
      )

  private fun setProviderRepository(repository: AuthRepository?) {
    AuthRepositoryProvider::class.java.getDeclaredField("instance").apply {
      isAccessible = true
      set(AuthRepositoryProvider, repository)
    }
  }
}
