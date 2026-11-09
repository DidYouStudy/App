// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the provider's production default (it reads the real raw resource) and its test
 * override.
 */
@RunWith(RobolectricTestRunner::class)
class CampusLocationRepositoryProviderTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

  /** Starts each test from no override. */
  @Before fun setUp() = CampusLocationRepositoryProvider.setRepository(null)

  /** Clears the override so no repository leaks into the next test. */
  @After fun tearDown() = CampusLocationRepositoryProvider.setRepository(null)

  @Test
  fun default_readsTheBundledCampusLocations() = runTest {
    val repo = CampusLocationRepositoryProvider.getRepository(context)

    val all = repo.getAllLocations().getOrThrow()
    assertTrue(all.isNotEmpty())
    assertTrue(all.none { it.type == CampusLocationType.UNKNOWN })
    assertTrue("CM" in repo.getZones().getOrThrow())
    // A blank or padded building code would find no rooms, or every room, when picked.
    assertTrue(repo.getZones().getOrThrow().all { it.isNotBlank() && it == it.trim() })
  }

  @Test
  fun getRepository_returnsTheSameInstanceEachTime() {
    assertSame(
        CampusLocationRepositoryProvider.getRepository(context),
        CampusLocationRepositoryProvider.getRepository(context),
    )
  }

  @Test
  fun setRepository_overridesAndNullRestoresAFreshDefault() {
    val fake = LocalCampusLocationRepository({ "[]" })
    CampusLocationRepositoryProvider.setRepository(fake)
    assertSame(fake, CampusLocationRepositoryProvider.getRepository(context))

    CampusLocationRepositoryProvider.setRepository(null)
    assertNotSame(fake, CampusLocationRepositoryProvider.getRepository(context))
  }
}
