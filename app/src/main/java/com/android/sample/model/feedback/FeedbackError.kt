// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

/** Categories of failures returned by session feedback operations. */
enum class FeedbackError {
  /** An argument is blank, malformed, out of range, or exceeds a limit. */
  INVALID_ARGUMENT,
  /** An interval is already running. */
  ALREADY_STARTED,
  /** No interval is running to stop. */
  NOT_STARTED,
  /** The caller is not allowed to perform the operation. */
  PERMISSION_DENIED,
  /** Stored feedback data is invalid. */
  INVALID_DATA,
  /** Feedback storage failed. */
  STORAGE_FAILURE,
  /** An unexpected failure occurred. */
  UNKNOWN,
}

/** Exception wrapping a [FeedbackError] returned by a feedback repository. */
class FeedbackException(
    /** The category of feedback failure. */
    val error: FeedbackError,
    cause: Throwable? = null,
) : Exception(error.name, cause)
