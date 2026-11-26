// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.studylog

/** Categories of failures returned by session study log operations. */
enum class StudyLogError {
  /** An argument is blank, malformed, out of range, or exceeds a limit. */
  INVALID_ARGUMENT,
  /** An interval is already running. */
  ALREADY_STARTED,
  /** No interval is running to stop. */
  NOT_STARTED,
  /** The caller is not allowed to perform the operation. */
  PERMISSION_DENIED,
  /** Stored study log data is invalid. */
  INVALID_DATA,
  /** study log storage failed. */
  STORAGE_FAILURE,
  /** An unexpected failure occurred. */
  UNKNOWN,
}

/** Exception wrapping a [StudyLogError] returned by a study log repository. */
class StudyLogException(
    /** The category of study log failure. */
    val error: StudyLogError,
    cause: Throwable? = null,
) : Exception(error.name, cause)
