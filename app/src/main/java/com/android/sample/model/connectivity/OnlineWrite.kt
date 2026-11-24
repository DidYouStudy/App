// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.connectivity

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * How long an online-only write may run before it is given up as failed.
 *
 * Firestore keeps a write waiting while the device cannot reach the server, so without a limit an
 * action started with a flaky connection could stay in progress forever.
 */
val ONLINE_WRITE_TIMEOUT: Duration = 15.seconds

/**
 * Failure reported by [runOnlineWrite] when the connection dropped, or the server did not answer in
 * time, while a write was running. The write may or may not have reached the server.
 */
class ConnectionLostException : Exception()

/**
 * Runs the server write [block] and guarantees it can neither hang nor outlive the connection.
 *
 * The write is cancelled and [ConnectionLostException] is returned as a failed [Result] when:
 * - [connectivityObserver] reports that the device went offline while [block] was running, or
 * - [block] did not finish within [timeout].
 *
 * Otherwise the result of [block] is returned unchanged. Cancellation of the caller is propagated
 * as usual. With a null [connectivityObserver] only the [timeout] applies.
 *
 * @param connectivityObserver source of connectivity, watched only while [block] runs.
 * @param timeout longest time [block] may take.
 * @param block the repository write to run.
 */
suspend fun <T> runOnlineWrite(
    connectivityObserver: ConnectivityObserver?,
    timeout: Duration = ONLINE_WRITE_TIMEOUT,
    block: suspend () -> Result<T>,
): Result<T> =
    try {
      withTimeout(timeout) {
        coroutineScope {
          // Throwing from the watcher cancels the sibling write and ends this scope with the
          // exception.
          val watcher = connectivityObserver?.let { observer ->
            launch {
              observer.observeIsOnline().first { isOnline -> !isOnline }
              throw ConnectionLostException()
            }
          }
          try {
            block()
          } finally {
            watcher?.cancel()
          }
        }
      }
    } catch (_: TimeoutCancellationException) {
      Result.failure(ConnectionLostException())
    } catch (exception: ConnectionLostException) {
      Result.failure(exception)
    }
