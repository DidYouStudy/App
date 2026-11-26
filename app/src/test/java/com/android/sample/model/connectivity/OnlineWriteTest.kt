// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.connectivity

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [runOnlineWrite]: a write that cannot hang, and that ends with a clear failure
 * when the connection is lost or the server does not answer in time.
 *
 * Time is virtual (`runTest`), so timeouts are checked without waiting.
 *
 * Note: a write block that never returns (`awaitCancellation()`) has type `Nothing`, so the type
 * argument of [runOnlineWrite] is given explicitly (`runOnlineWrite<Unit>`) in those tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnlineWriteTest {

  /**
   * A connectivity source that never reports anything and never completes, like a real one with no
   * change to report. Only the timeout can end a write watched by it.
   *
   * It must not complete: the watcher in [runOnlineWrite] waits for an offline value, and a flow
   * that ends without one (such as `emptyFlow()`) makes it fail with `NoSuchElementException`.
   */
  private val silentObserver = ConnectivityObserver { MutableSharedFlow() }

  // ---------------------------------------------------------------------------------------------
  // The write finishes normally
  // ---------------------------------------------------------------------------------------------

  @Test
  fun returnsTheSuccessOfTheWrite() = runTest {
    val result = runOnlineWrite(FakeConnectivityObserver()) { Result.success("saved") }

    assertEquals("saved", result.getOrNull())
  }

  /** A failure of the repository itself is not a connection problem and is passed on as is. */
  @Test
  fun returnsTheFailureOfTheWriteUnchanged() = runTest {
    val failure = IllegalStateException("rejected")

    val result = runOnlineWrite(FakeConnectivityObserver()) { Result.failure<Unit>(failure) }

    assertSame(failure, result.exceptionOrNull())
  }

  /** A failed [Result] that already holds a [ConnectionLostException] is returned untouched. */
  @Test
  fun returnsAConnectionLostFailureOfTheWriteAsIs() = runTest {
    val failure = ConnectionLostException()

    val result = runOnlineWrite(FakeConnectivityObserver()) { Result.failure<Unit>(failure) }

    assertSame(failure, result.exceptionOrNull())
  }

  @Test
  fun worksWithoutAConnectivityObserver() = runTest {
    val result = runOnlineWrite(null) { Result.success(Unit) }

    assertTrue(result.isSuccess)
  }

  @Test
  fun returnsTheFailureOfTheWriteWithoutAConnectivityObserver() = runTest {
    val failure = IllegalStateException("rejected")

    val result = runOnlineWrite(null) { Result.failure<Unit>(failure) }

    assertSame(failure, result.exceptionOrNull())
  }

  @Test
  fun aWriteThatTakesTimeButFinishesBeforeTheTimeoutSucceeds() = runTest {
    val result =
        runOnlineWrite(FakeConnectivityObserver(), timeout = 10.seconds) {
          delay(9.seconds)
          Result.success(Unit)
        }

    assertTrue(result.isSuccess)
    assertEquals(9_000L, currentTime)
  }

  /** Going offline after the write has finished changes nothing about its result. */
  @Test
  fun aWriteFinishingBeforeAnyConnectivityChangeIsNotAffected() = runTest {
    val connectivity = FakeConnectivityObserver()

    val result = runOnlineWrite(connectivity) { Result.success(1) }
    connectivity.isOnline.value = false
    runCurrent()

    assertEquals(1, result.getOrNull())
  }

  /** The connectivity collection must not outlive the write, or it would leak a callback. */
  @Test
  fun stopsWatchingConnectivityOnceTheWriteFinishes() = runTest {
    val connectivity = FakeConnectivityObserver()

    runOnlineWrite(connectivity) { Result.success(Unit) }
    runCurrent()

    assertEquals(0, connectivity.activeCollectors)
  }

  @Test
  fun stopsWatchingConnectivityOnceTheWriteFails() = runTest {
    val connectivity = FakeConnectivityObserver()

    runOnlineWrite(connectivity) { Result.failure<Unit>(IllegalStateException()) }
    runCurrent()

    assertEquals(0, connectivity.activeCollectors)
  }

  @Test
  fun watchesConnectivityWhileTheWriteRuns() = runTest {
    val connectivity = FakeConnectivityObserver()
    val release = CompletableDeferred<Unit>()
    val write = async {
      runOnlineWrite(connectivity) {
        release.await()
        Result.success(Unit)
      }
    }
    runCurrent()

    assertEquals(1, connectivity.activeCollectors)

    release.complete(Unit)
    write.await()
  }

  // ---------------------------------------------------------------------------------------------
  // The connection is lost
  // ---------------------------------------------------------------------------------------------

  /** The acceptance case: losing the connection in the middle of an action fails it clearly. */
  @Test
  fun failsWithConnectionLostWhenTheConnectionDropsDuringTheWrite() = runTest {
    val connectivity = FakeConnectivityObserver()
    val write = async { runOnlineWrite<Unit>(connectivity) { awaitCancellation() } }
    runCurrent()

    connectivity.isOnline.value = false
    runCurrent()

    assertTrue(write.isCompleted)
    assertTrue(write.await().exceptionOrNull() is ConnectionLostException)
  }

  /** The hanging write is cancelled, not left running in the background. */
  @Test
  fun cancelsTheWriteWhenTheConnectionDrops() = runTest {
    val connectivity = FakeConnectivityObserver()
    var cancelled = false
    val write = async {
      runOnlineWrite<Unit>(connectivity) {
        try {
          awaitCancellation()
        } finally {
          cancelled = true
        }
      }
    }
    runCurrent()

    connectivity.isOnline.value = false
    runCurrent()
    write.await()

    assertTrue(cancelled)
  }

  /** The failure is reported as soon as the connection drops, without waiting for the timeout. */
  @Test
  fun failsAtTheMomentTheConnectionDropsNotAtTheTimeout() = runTest {
    val connectivity = FakeConnectivityObserver()
    val write = async {
      runOnlineWrite<Unit>(connectivity, timeout = 10.seconds) { awaitCancellation() }
    }
    advanceTimeBy(3.seconds)

    connectivity.isOnline.value = false
    runCurrent()

    assertTrue(write.isCompleted)
    assertTrue(write.await().exceptionOrNull() is ConnectionLostException)
    assertEquals(3_000L, currentTime)
  }

  @Test
  fun failsAtOnceWhenTheDeviceIsAlreadyOffline() = runTest {
    val connectivity = FakeConnectivityObserver(initialOnline = false)

    val result = runOnlineWrite<Unit>(connectivity) { awaitCancellation() }

    assertTrue(result.exceptionOrNull() is ConnectionLostException)
    assertEquals(0L, currentTime)
  }

  /** An outage that ends again while the write is pending still fails it: it may be lost. */
  @Test
  fun aBriefOutageDuringTheWriteStillFailsIt() = runTest {
    val connectivity = FakeConnectivityObserver()
    val write = async { runOnlineWrite<Unit>(connectivity) { awaitCancellation() } }
    runCurrent()

    connectivity.isOnline.value = false
    runCurrent()
    connectivity.isOnline.value = true
    runCurrent()

    assertTrue(write.await().exceptionOrNull() is ConnectionLostException)
  }

  /** The connection returning while the write runs does not by itself end it. */
  @Test
  fun stayingOnlineLetsTheWriteFinishLater() = runTest {
    val connectivity = FakeConnectivityObserver()
    val release = CompletableDeferred<Unit>()
    val write = async {
      runOnlineWrite(connectivity) {
        release.await()
        Result.success("ok")
      }
    }
    runCurrent()

    connectivity.isOnline.value = true
    runCurrent()
    assertFalse(write.isCompleted)
    release.complete(Unit)

    assertEquals("ok", write.await().getOrNull())
  }

  @Test
  fun stopsWatchingConnectivityAfterAConnectionLoss() = runTest {
    val connectivity = FakeConnectivityObserver()
    val write = async { runOnlineWrite<Unit>(connectivity) { awaitCancellation() } }
    runCurrent()

    connectivity.isOnline.value = false
    runCurrent()
    write.await()

    assertEquals(0, connectivity.activeCollectors)
  }

  /** Every write running at the time of the outage is failed, each with its own exception. */
  @Test
  fun aConnectionLossFailsEveryWriteThatIsRunning() = runTest {
    val connectivity = FakeConnectivityObserver()
    val first = async { runOnlineWrite<Unit>(connectivity) { awaitCancellation() } }
    val second = async { runOnlineWrite<Unit>(connectivity) { awaitCancellation() } }
    runCurrent()
    assertEquals(2, connectivity.activeCollectors)

    connectivity.isOnline.value = false
    runCurrent()

    val firstFailure = first.await().exceptionOrNull()
    val secondFailure = second.await().exceptionOrNull()
    assertTrue(firstFailure is ConnectionLostException)
    assertTrue(secondFailure is ConnectionLostException)
    assertNotSame(firstFailure, secondFailure)
    assertEquals(0, connectivity.activeCollectors)
  }

  /** An observer that never reports anything leaves only the timeout as protection. */
  @Test
  fun anObserverThatEmitsNothingNeverCancelsAFastWrite() = runTest {
    val result = runOnlineWrite(silentObserver) { Result.success(5) }

    assertEquals(5, result.getOrNull())
  }

  @Test
  fun anObserverThatEmitsNothingStillLetsTheTimeoutApply() = runTest {
    val result = runOnlineWrite<Unit>(silentObserver, timeout = 3.seconds) { awaitCancellation() }

    assertTrue(result.exceptionOrNull() is ConnectionLostException)
    assertEquals(3_000L, currentTime)
  }

  // ---------------------------------------------------------------------------------------------
  // The server does not answer
  // ---------------------------------------------------------------------------------------------

  /** The "nothing hangs" case: a write that never completes fails once the timeout passes. */
  @Test
  fun failsWithConnectionLostWhenTheWriteTimesOut() = runTest {
    val write = async {
      runOnlineWrite<Unit>(FakeConnectivityObserver(), timeout = 5.seconds) { awaitCancellation() }
    }

    advanceTimeBy(4_999.milliseconds)
    runCurrent()
    assertFalse(write.isCompleted)

    advanceTimeBy(2.milliseconds)
    runCurrent()

    assertTrue(write.isCompleted)
    assertTrue(write.await().exceptionOrNull() is ConnectionLostException)
  }

  @Test
  fun timesOutWithoutAConnectivityObserverToo() = runTest {
    val result = runOnlineWrite<Unit>(null, timeout = 2.seconds) { awaitCancellation() }

    assertTrue(result.exceptionOrNull() is ConnectionLostException)
    assertEquals(2_000L, currentTime)
  }

  @Test
  fun cancelsTheWriteWhenItTimesOut() = runTest {
    var cancelled = false

    runOnlineWrite<Unit>(null, timeout = 1.seconds) {
      try {
        awaitCancellation()
      } finally {
        cancelled = true
      }
    }

    assertTrue(cancelled)
  }

  @Test
  fun usesTheDefaultTimeoutWhenNoneIsGiven() = runTest {
    val write = async { runOnlineWrite<Unit>(FakeConnectivityObserver()) { awaitCancellation() } }

    advanceTimeBy(ONLINE_WRITE_TIMEOUT - 1.milliseconds)
    runCurrent()
    assertFalse(write.isCompleted)

    advanceTimeBy(2.milliseconds)
    runCurrent()

    assertTrue(write.await().exceptionOrNull() is ConnectionLostException)
  }

  @Test
  fun theDefaultTimeoutIsFifteenSeconds() {
    assertEquals(15.seconds, ONLINE_WRITE_TIMEOUT)
  }

  /** A timeout that is already used up fails the write before it even starts. */
  @Test
  fun aZeroTimeoutFailsWithoutRunningTheWrite() = runTest {
    var started = false

    val result =
        runOnlineWrite(null, timeout = Duration.ZERO) {
          started = true
          Result.success(Unit)
        }

    assertFalse(started)
    assertTrue(result.exceptionOrNull() is ConnectionLostException)
  }

  @Test
  fun stopsWatchingConnectivityAfterATimeout() = runTest {
    val connectivity = FakeConnectivityObserver()

    runOnlineWrite<Unit>(connectivity, timeout = 1.seconds) { awaitCancellation() }
    runCurrent()

    assertEquals(0, connectivity.activeCollectors)
  }

  // ---------------------------------------------------------------------------------------------
  // Errors and cancellation of the caller
  // ---------------------------------------------------------------------------------------------

  /** Only connection problems become results; a bug in the write still surfaces. */
  @Test
  fun anUnexpectedExceptionOfTheWriteIsNotSwallowed() {
    assertThrows(IllegalStateException::class.java) {
      runTest {
        runOnlineWrite<Unit>(FakeConnectivityObserver()) { throw IllegalStateException("bug") }
      }
    }
  }

  @Test
  fun anUnexpectedExceptionOfTheWriteIsNotSwallowedWithoutAnObserver() {
    assertThrows(IllegalStateException::class.java) {
      runTest { runOnlineWrite<Unit>(null) { throw IllegalStateException("bug") } }
    }
  }

  /** A broken connectivity source is a bug too, and is not reported as a lost connection. */
  @Test
  fun anUnexpectedExceptionOfTheObserverIsNotSwallowed() {
    val brokenObserver = ConnectivityObserver { flow { throw IllegalStateException("observer") } }

    assertThrows(IllegalStateException::class.java) {
      runTest { runOnlineWrite<Unit>(brokenObserver) { awaitCancellation() } }
    }
  }

  /**
   * When the caller itself is cancelled (screen closed), cancellation is not turned into a result.
   */
  @Test
  fun cancellingTheCallerCancelsTheWriteAndStopsWatching() = runTest {
    val connectivity = FakeConnectivityObserver()
    var writeCancelled = false
    val caller = launch {
      runOnlineWrite<Unit>(connectivity) {
        try {
          awaitCancellation()
        } finally {
          writeCancelled = true
        }
      }
    }
    runCurrent()

    caller.cancelAndJoin()

    assertTrue(caller.isCancelled)
    assertTrue(writeCancelled)
    assertEquals(0, connectivity.activeCollectors)
  }

  @Test
  fun cancellingTheCallerDoesNotProduceAFailedResult() = runTest {
    val connectivity = FakeConnectivityObserver()
    val write = async { runOnlineWrite<Unit>(connectivity) { awaitCancellation() } }
    runCurrent()

    write.cancelAndJoin()

    assertTrue(write.isCancelled)
    assertTrue(write.getCompletionExceptionOrNull() is CancellationException)
  }

  @Test
  fun connectionLostExceptionIsAnException() {
    assertTrue(Exception::class.java.isAssignableFrom(ConnectionLostException::class.java))
  }

  /** It must not be a cancellation, or coroutines would treat a lost connection as a cancel. */
  @Test
  fun connectionLostExceptionIsNotACancellation() {
    assertFalse(
        CancellationException::class.java.isAssignableFrom(ConnectionLostException::class.java)
    )
  }

  /**
   * The write finishes (and the connection drops) without the write ever suspending, so the watcher
   * never gets to run and the finished write keeps its result.
   */
  @Test
  fun anOutageDoesNotReplaceASuccessfulRepositoryResultThatAlreadyCompleted() = runTest {
    val connectivity = FakeConnectivityObserver()
    val result =
        runOnlineWrite(connectivity) {
          connectivity.isOnline.value = false
          Result.success("saved-before-loss")
        }

    assertEquals("saved-before-loss", result.getOrNull())
  }
}
