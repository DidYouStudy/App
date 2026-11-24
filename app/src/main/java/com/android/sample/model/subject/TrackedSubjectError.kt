// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

/** Categories for failures while observing or saving subject tracking decisions. */
enum class TrackedSubjectError {
  /** The caller passed a blank owner ID or an invalid subject parameter. */
  INVALID_ARGUMENT,

  /** Storage rejected the operation for the current user. */
  PERMISSION_DENIED,

  /** Stored decision data could not be converted to [SubjectDecisions]. */
  INVALID_DATA,

  /** Storage failed or could not complete the operation. */
  STORAGE_FAILURE,

  /** The operation failed for an unexpected reason. */
  UNKNOWN,
}

/** Exception wrapping a [TrackedSubjectError] for subject repository operations. */
class TrackedSubjectException(
    val error: TrackedSubjectError,
    cause: Throwable? = null,
) : Exception(error.name, cause)
