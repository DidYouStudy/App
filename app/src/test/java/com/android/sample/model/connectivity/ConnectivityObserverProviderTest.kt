// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.connectivity

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Unit tests for [ConnectivityObserverProvider]: one shared default, replaceable for tests. */
@RunWith(RobolectricTestRunner::class)
class ConnectivityObserverProviderTest {

  private val context = ApplicationProvider.getApplicationContext<Application>()

  @After
  fun tearDown() {
    ConnectivityObserverProvider.setObserver(null)
  }

  @Test
  fun defaultObserverIsSharedAndBuiltOverTheAndroidObserver() {
    ConnectivityObserverProvider.setObserver(null)

    val first = ConnectivityObserverProvider.getObserver(context)
    val second = ConnectivityObserverProvider.getObserver(context)

    assertSame(first, second)
    assertTrue(first is SharedConnectivityObserver)
  }

  @Test
  fun replacementIsReturnedUntilItIsRemoved() {
    val fake = FakeConnectivityObserver()
    ConnectivityObserverProvider.setObserver(fake)

    assertSame(fake, ConnectivityObserverProvider.getObserver(context))

    ConnectivityObserverProvider.setObserver(null)
    assertNotSame(fake, ConnectivityObserverProvider.getObserver(context))
  }
}
