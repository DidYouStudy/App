// Co-authored-by: Gemini AI Agent
package com.android.sample.model.preferences

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesRepositorySyncTest {
  private lateinit var localStore: FakeLocalStore
  private lateinit var remoteRepository: FakeRemoteRepository
  private lateinit var syncRepository: PreferencesRepositorySync
  private val testDispatcher = UnconfinedTestDispatcher()

  private class FakeLocalStore : PreferencesLocalStore {
    val map = mutableMapOf<String, UserPreferences>()

    override fun get(userId: String): UserPreferences? = map[userId]

    override fun save(preferences: UserPreferences) {
      map[preferences.userId] = preferences
    }

    override fun has(userId: String): Boolean = map.containsKey(userId)

    override fun clear(userId: String) {
      map.remove(userId)
    }
  }

  private class FakeRemoteRepository : PreferencesRepository {
    val map = mutableMapOf<String, UserPreferences>()
    var getCalls = 0
    var saveCalls = 0
    var shouldFail = false

    override fun hasPreferences(userId: String): Boolean = false

    override suspend fun getPreferences(userId: String): Result<UserPreferences?> {
      getCalls++
      if (shouldFail) return Result.failure(RuntimeException("Remote failure"))
      return Result.success(map[userId])
    }

    override suspend fun savePreferences(preferences: UserPreferences): Result<Unit> {
      saveCalls++
      if (shouldFail) return Result.failure(RuntimeException("Remote failure"))
      map[preferences.userId] = preferences
      return Result.success(Unit)
    }
  }

  @Before
  fun setUp() {
    localStore = FakeLocalStore()
    remoteRepository = FakeRemoteRepository()
    syncRepository = PreferencesRepositorySync(localStore, remoteRepository, testDispatcher)
  }

  @Test
  fun hasPreferencesDelegatesToLocalStore() {
    assertFalse(syncRepository.hasPreferences("user1"))
    localStore.save(UserPreferences(userId = "user1"))
    assertTrue(syncRepository.hasPreferences("user1"))
  }

  @Test
  fun getPreferencesReturnsLocalPreferencesWhenPresentAndSyncsRemoteIfDifferent() = runTest {
    val local = UserPreferences(userId = "user1", sessionLengthMinutes = 45)
    val outdatedRemote = UserPreferences(userId = "user1", sessionLengthMinutes = 30)

    localStore.save(local)
    remoteRepository.map["user1"] = outdatedRemote

    val result = syncRepository.getPreferences("user1")
    assertTrue(result.isSuccess)
    assertEquals(local, result.getOrNull())

    // Remote should have been updated to match local reference
    assertEquals(local, remoteRepository.map["user1"])
    assertEquals(1, remoteRepository.saveCalls)
  }

  @Test
  fun getPreferencesReturnsLocalPreferencesWhenRemoteIsOffline() = runTest {
    val local = UserPreferences(userId = "user1", sessionLengthMinutes = 45)
    localStore.save(local)
    remoteRepository.shouldFail = true

    val result = syncRepository.getPreferences("user1")
    assertTrue(result.isSuccess)
    assertEquals(local, result.getOrNull())
  }

  @Test
  fun getPreferencesFetchesRemoteAndCachesLocallyWhenLocalIsAbsent() = runTest {
    val remote = UserPreferences(userId = "user2", sessionLengthMinutes = 90)
    remoteRepository.map["user2"] = remote

    assertFalse(syncRepository.hasPreferences("user2"))

    val result = syncRepository.getPreferences("user2")
    assertTrue(result.isSuccess)
    assertEquals(remote, result.getOrNull())

    // Cached in local store
    assertTrue(syncRepository.hasPreferences("user2"))
    assertEquals(remote, localStore.get("user2"))
  }

  @Test
  fun getPreferencesReturnsNullWhenBothLocalAndRemoteAreAbsent() = runTest {
    val result = syncRepository.getPreferences("user3")
    assertTrue(result.isSuccess)
    assertNull(result.getOrNull())
    assertFalse(syncRepository.hasPreferences("user3"))
  }

  @Test
  fun savePreferencesSavesLocallyAndPushesToRemote() = runTest {
    val prefs = UserPreferences(userId = "user4", sessionLengthMinutes = 120)

    val result = syncRepository.savePreferences(prefs)
    assertTrue(result.isSuccess)

    assertEquals(prefs, localStore.get("user4"))
    assertEquals(prefs, remoteRepository.map["user4"])
    assertEquals(1, remoteRepository.saveCalls)
  }

  @Test
  fun savePreferencesSucceedsLocallyEvenWhenRemoteFails() = runTest {
    val prefs = UserPreferences(userId = "user5", sessionLengthMinutes = 60)
    remoteRepository.shouldFail = true

    val result = syncRepository.savePreferences(prefs)
    assertTrue(result.isSuccess)

    // Saved locally despite remote error
    assertEquals(prefs, localStore.get("user5"))
    assertTrue(syncRepository.hasPreferences("user5"))
  }
}
