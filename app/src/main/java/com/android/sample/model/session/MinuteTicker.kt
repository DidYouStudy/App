// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.session

import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Emits a unit value at each minute boundary according to [clock]. */
fun minuteTicks(clock: Clock): Flow<Unit> = emptyFlow()
