// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.time.Duration
import java.time.Instant

/**
 * Longest allowed activity (event or group session); catches input mistakes such as a wrong end
 * date. Single source of truth shared by every model that validates through [requireValidActivity].
 */
val ACTIVITY_MAX_DURATION: Duration = Duration.ofHours(24)

/**
 * Checks the invariants common to events and group sessions, so the two models cannot silently
 * diverge. Model-specific checks (owner, members, tasks, import key) stay in the models.
 *
 * @param activityName human-readable model name used in the duration error message, e.g. "Event".
 * @param type activity kind; only [EventType.OTHER] may omit the subject.
 * @param subject subject label; nonblank unless null for [EventType.OTHER].
 * @param startsAt planned start, inclusive.
 * @param endsAt planned end, exclusive; must be after [startsAt] and at most
 *   [ACTIVITY_MAX_DURATION] later.
 * @param location optional place label; must be nonblank when present.
 * @throws IllegalArgumentException if any shared invariant is violated.
 */
fun requireValidActivity(
    activityName: String,
    type: EventType,
    subject: String?,
    startsAt: Instant,
    endsAt: Instant,
    location: String?,
) {
  require(subject?.isNotBlank() ?: (type == EventType.OTHER)) {
    "Subject must not be blank, and may only be absent for OTHER"
  }
  require(endsAt.isAfter(startsAt)) { "End must be after start" }
  require(Duration.between(startsAt, endsAt) <= ACTIVITY_MAX_DURATION) {
    "$activityName must not last longer than $ACTIVITY_MAX_DURATION"
  }
  require(location == null || location.isNotBlank()) { "Location must not be blank" }
}
