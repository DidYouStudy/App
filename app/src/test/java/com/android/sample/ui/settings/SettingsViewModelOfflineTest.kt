// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.settings

import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.connectivity.FakeConnectivityObserver
import com.android.sample.model.profile.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Unit tests for the offline indication of [SettingsViewModel] (`SettingsUiState.isOffline`). */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelOfflineTest {

  private val currentUser = MutableStateFlow<UserAccount?>(null)
  private lateinit var authRepository: AuthRepository
  private val repository = FakeProfileRepository(UserProfile(uid = "u1", username = "alice"))

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    authRepository = mock()
    whenever(authRepository.currentUser).thenReturn(currentUser)
    currentUser.value = UserAccount(uid = "u1", email = "a@b.c", displayName = "A", photoUrl = null)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // Starts collecting the state, as the screen does, so connectivity is observed.
  private fun SettingsViewModel.observe(scope: TestScope): Job =
      scope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { uiState.collect {} }

  @Test
  fun isOffline_isFalse_whenNoObserverIsProvided() = runTest {
    val vm = SettingsViewModel(authRepository, repository)
    val job = vm.observe(this)

    assertFalse(vm.uiState.value.isOffline)
    job.cancel()
  }

  @Test
  fun isOffline_followsConnectivity_whileObserved() = runTest {
    val connectivity = FakeConnectivityObserver(initialOnline = false)
    val vm = SettingsViewModel(authRepository, repository, connectivity)
    val job = vm.observe(this)

    assertTrue(vm.uiState.value.isOffline)
    connectivity.isOnline.value = true
    assertFalse(vm.uiState.value.isOffline)
    connectivity.isOnline.value = false
    assertTrue(vm.uiState.value.isOffline)
    job.cancel()
  }

  @Test
  fun isOffline_survivesSignOut() = runTest {
    val connectivity = FakeConnectivityObserver(initialOnline = false)
    val vm = SettingsViewModel(authRepository, repository, connectivity)
    val job = vm.observe(this)

    currentUser.value = null

    assertTrue(vm.uiState.value.isOffline)
    job.cancel()
  }

  @Test
  fun connectivity_isReleased_whenNobodyObservesTheState() = runTest {
    val connectivity = FakeConnectivityObserver()
    val vm = SettingsViewModel(authRepository, repository, connectivity)
    val job = vm.observe(this)
    assertEquals(1, connectivity.activeCollectors)

    job.cancel()

    assertEquals(0, connectivity.activeCollectors)
  }
}
