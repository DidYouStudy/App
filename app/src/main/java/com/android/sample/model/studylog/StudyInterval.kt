// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.studylog

import java.time.Instant

/** A continuous period during which a student studied. */
data class StudyInterval(
    /** The instant at which studying started. */
    val startedAt: Instant,
    /** The instant at which studying ended, or null while the interval is running. */
    val endedAt: Instant? = null,
) {
  init {
    require(endedAt == null || endedAt.isAfter(startedAt)) { "endedAt must be after startedAt" }
  }
}
