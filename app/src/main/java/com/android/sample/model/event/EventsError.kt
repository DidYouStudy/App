// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

/**
 * Stable categories for failures while reading or writing events.
 *
 * The category lets ViewModels and tests handle event failures without depending on Firebase
 * exception classes or provider-specific message text.
 */
enum class EventsError {
  /** The caller passed a blank ID or an empty time range. */
  INVALID_ARGUMENT,

  /** No event with this ID exists for this owner. */
  NOT_FOUND,

  /** The storage rejected the operation for the current user. */
  PERMISSION_DENIED,

  /** A stored event could not be converted to [Event]. */
  INVALID_EVENT_DATA,

  /** The storage failed or could not complete the operation. */
  STORAGE_FAILURE,

  /** The operation failed for an unexpected reason. */
  UNKNOWN,
}
