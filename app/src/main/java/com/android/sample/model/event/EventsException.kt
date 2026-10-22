// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

/**
 * Domain exception returned inside a failed [EventsRepository] [Result].
 *
 * @param error stable category describing the failure.
 * @param cause underlying exception, when available.
 */
class EventsException(val error: EventsError, cause: Throwable? = null) :
    Exception("Events operation failed: $error", cause)
