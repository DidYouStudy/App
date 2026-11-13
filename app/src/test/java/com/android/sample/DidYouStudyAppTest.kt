// Co-authored-by: Gemini AI Agent
package com.android.sample

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventsRepositoryProvider
import com.android.sample.model.event.InMemoryEventsRepository
import com.android.sample.model.groupsession.GroupSessionsRepositoryProvider
import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import com.android.sample.model.preferences.OnboardingStore
import com.android.sample.model.preferences.OnboardingStoreProvider
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.PreferencesRepositoryProvider
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.ProfileRepositoryProvider
import com.android.sample.model.profile.UserProfile
import com.android.sample.ui.app.AppViewModel
import com.android.sample.ui.auth.SignInScreenTestTags
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.preferences.PreferencesScreenTestTags
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Unit tests for [DidYouStudyApp] covering loading, destination resolution, and logout flow. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class DidYouStudyAppTest {

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setUp() {
    EventsRepositoryProvider.setRepository(InMemoryEventsRepository())
    GroupSessionsRepositoryProvider.setRepository(InMemoryGroupSessionsRepository())
    ProfileRepositoryProvider.setRepository(FakeProfileRepository())
  }

  @After
  fun tearDown() {
    AuthRepositoryProvider.setRepository(null)
    PreferencesRepositoryProvider.setRepository(null)
    ProfileRepositoryProvider.setRepository(null)
    EventsRepositoryProvider.setRepository(null)
    GroupSessionsRepositoryProvider.setRepository(null)
    OnboardingStoreProvider.setStore(null)
  }

  /**
   * Verifies that when an authenticated user has an unknown onboarding state, [DidYouStudyApp]
   * displays the loading indicator while waiting for destination resolution.
   */
  @Test
  fun didYouStudyApp_showsLoadingSpinner_whenStartDestinationIsNull() {
    val user = UserAccount("loading-user", null, null, null)
    val auth = FakeAuthRepository(user)
    val preferencesDeferred = CompletableDeferred<Result<UserPreferences?>>()
    val preferences = DeferredPreferencesRepository(preferencesDeferred)
    val onboardingStore = FakeOnboardingStore()

    AuthRepositoryProvider.setRepository(auth)
    PreferencesRepositoryProvider.setRepository(preferences)
    OnboardingStoreProvider.setStore(onboardingStore)

    val viewModel = AppViewModel(auth, preferences, onboardingStore)

    composeRule.setContent { DidYouStudyApp(applicationViewModel = viewModel) }

    composeRule.onNodeWithTag(NavigationTestTags.LOADING_INDICATOR).assertIsDisplayed()
  }

  /**
   * Verifies that when preferences finish loading for a returning user, [DidYouStudyApp]
   * transitions from the loading spinner to the dashboard.
   */
  @Test
  fun didYouStudyApp_resolvesDestinationToDashboard_whenPreferencesLoaded() {
    val user = UserAccount("existing-user", null, null, null)
    val auth = FakeAuthRepository(user)
    val preferencesDeferred = CompletableDeferred<Result<UserPreferences?>>()
    val preferences = DeferredPreferencesRepository(preferencesDeferred)
    val onboardingStore = FakeOnboardingStore()

    AuthRepositoryProvider.setRepository(auth)
    PreferencesRepositoryProvider.setRepository(preferences)
    OnboardingStoreProvider.setStore(onboardingStore)

    val viewModel = AppViewModel(auth, preferences, onboardingStore)

    composeRule.setContent { DidYouStudyApp(applicationViewModel = viewModel) }

    composeRule.onNodeWithTag(NavigationTestTags.LOADING_INDICATOR).assertIsDisplayed()

    preferencesDeferred.complete(Result.success(UserPreferences(userId = user.uid)))

    composeRule.waitForIdle()

    composeRule.onNodeWithTag(NavigationTestTags.LOADING_INDICATOR).assertDoesNotExist()
    composeRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  /**
   * Verifies that when preferences finish loading and no preferences exist, [DidYouStudyApp]
   * resolves to the preferences onboarding screen.
   */
  @Test
  fun didYouStudyApp_resolvesDestinationToPreferences_whenNoPreferencesFound() {
    val user = UserAccount("new-user", null, null, null)
    val auth = FakeAuthRepository(user)
    val preferencesDeferred = CompletableDeferred<Result<UserPreferences?>>()
    val preferences = DeferredPreferencesRepository(preferencesDeferred)
    val onboardingStore = FakeOnboardingStore()

    AuthRepositoryProvider.setRepository(auth)
    PreferencesRepositoryProvider.setRepository(preferences)
    OnboardingStoreProvider.setStore(onboardingStore)

    val viewModel = AppViewModel(auth, preferences, onboardingStore)

    composeRule.setContent { DidYouStudyApp(applicationViewModel = viewModel) }

    composeRule.onNodeWithTag(NavigationTestTags.LOADING_INDICATOR).assertIsDisplayed()

    preferencesDeferred.complete(Result.success(null))

    composeRule.waitForIdle()

    composeRule.onNodeWithTag(NavigationTestTags.LOADING_INDICATOR).assertDoesNotExist()
    composeRule.onNodeWithTag(PreferencesScreenTestTags.PREFERENCE_SCREEN).assertIsDisplayed()
  }

  /**
   * Verifies that when the user logs out, [DidYouStudyApp] handles the logout state and navigates
   * to the authentication screen.
   */
  @Test
  fun didYouStudyApp_navigatesToAuth_onLogOut() {
    val user = UserAccount("logged-in-user", null, null, null)
    val auth = FakeAuthRepository(user)
    val preferences = DeferredPreferencesRepository()
    val onboardingStore = FakeOnboardingStore(mapOf(user.uid to true))

    AuthRepositoryProvider.setRepository(auth)
    PreferencesRepositoryProvider.setRepository(preferences)
    OnboardingStoreProvider.setStore(onboardingStore)

    val viewModel = AppViewModel(auth, preferences, onboardingStore)

    composeRule.setContent { DidYouStudyApp(applicationViewModel = viewModel) }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()

    runBlocking { auth.signOut() }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag(SignInScreenTestTags.SCREEN).assertIsDisplayed()
  }

  /** In-memory [OnboardingStore] fake for controlling onboarding flags in tests. */
  private class FakeOnboardingStore(
      initialState: Map<String, Boolean> = emptyMap(),
  ) : OnboardingStore {
    private val state = initialState.toMutableMap()

    override fun hasCompletedOnboarding(userId: String): Boolean? = state[userId]

    override fun setCompletedOnboarding(userId: String, completed: Boolean) {
      state[userId] = completed
    }

    override fun clearOnboarding(userId: String) {
      state.remove(userId)
    }
  }

  /** Fake [AuthRepository] allowing explicit sign out and reactive state updates. */
  private class FakeAuthRepository(user: UserAccount?) : AuthRepository {
    private val currentUserState = MutableStateFlow<UserAccount?>(user)
    override val currentUser: StateFlow<UserAccount?> = currentUserState

    override val isAuthenticated: Boolean
      get() = currentUserState.value != null

    override val currentUserId: String?
      get() = currentUserState.value?.uid

    override suspend fun signInWithGoogle(idToken: String): UserAccount =
        currentUserState.value ?: throw IllegalStateException("Not signed in")

    override suspend fun signOut() {
      currentUserState.value = null
    }
  }

  /** Fake [PreferencesRepository] supporting asynchronous completion via [CompletableDeferred]. */
  private class DeferredPreferencesRepository(
      private val deferred: CompletableDeferred<Result<UserPreferences?>>? = null,
  ) : PreferencesRepository {
    override suspend fun getPreferences(userId: String): Result<UserPreferences?> =
        deferred?.await() ?: Result.success(UserPreferences(userId = userId))

    override suspend fun savePreferences(preferences: UserPreferences): Result<Unit> =
        Result.success(Unit)
  }

  /** Fake [ProfileRepository] providing empty profile results. */
  private class FakeProfileRepository : ProfileRepository {
    override suspend fun getProfile(userId: String): Result<UserProfile?> = Result.success(null)

    override suspend fun createProfile(userId: String): Result<UserProfile> =
        Result.failure(UnsupportedOperationException("Not needed in navigation tests"))
  }
}
