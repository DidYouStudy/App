// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.session

import java.time.Clock
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits a unit value at each minute boundary according to [clock]. */
fun minuteTicks(clock: Clock): Flow<Unit> = flow {
  while (true) {
    val now = clock.instant()
    val nextMinute = now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES)
    delay(java.time.Duration.between(now, nextMinute).toMillis())
    emit(Unit)
  }
}
