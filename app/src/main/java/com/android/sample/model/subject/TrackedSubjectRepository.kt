// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import kotlinx.coroutines.flow.Flow

/**
 * Stores and observes a student's decisions on which subjects to track.
 *
 * Failures are returned as [Result.failure] containing a [TrackedSubjectException]; cancellation is
 * propagated rather than converted into a failed [Result].
 *
 * Writes complete once stored in the local cache, without waiting for the server, so they also work
 * offline; the storage syncs them later. A server-side rejection of the queued write is not
 * reported.
 */
interface TrackedSubjectRepository {
  /**
   * Observes the owner's subject tracking decisions.
   *
   * Emits the current decisions, then again after every change. An absent document yields an empty
   * [SubjectDecisions].
   *
   * @return a flow emitting [Result.failure] with [TrackedSubjectError.INVALID_ARGUMENT] when
   *   [ownerId] is blank or contains a slash ('/').
   */
  fun observeDecisions(ownerId: String): Flow<Result<SubjectDecisions>>

  /**
   * Saves tracking decisions for the owner, merging with existing choices.
   *
   * Decisions for other subjects are preserved. Existing decisions with the same subject key are
   * overwritten with the new flag and latest spelling. Passing an empty map succeeds without
   * writing.
   *
   * @param ownerId user ID, must not be blank or contain a slash ('/').
   * @param decisions map of subject spelling to tracked boolean flag.
   * @return [Result.success] on success, or [Result.failure] with
   *   [TrackedSubjectError.INVALID_ARGUMENT] if [ownerId] is blank or contains a slash ('/'), any
   *   subject spelling is blank, or two subjects share the same key.
   */
  suspend fun saveDecisions(ownerId: String, decisions: Map<String, Boolean>): Result<Unit>

  /**
   * Saves how important [subject] is to the owner, replacing any earlier priority for it.
   *
   * Only the priority changes: the tracked flag, the stored spelling and every other subject are
   * left as they are. The subject must already be tracked (see [saveDecisions]); custom subjects
   * that were never confirmed as tracked have no priority. Subject comparison ignores case and
   * surrounding whitespace.
   *
   * @param ownerId user ID, must not be blank or contain a slash ('/').
   * @param subject spelling of the tracked subject to rate.
   * @param priority the new priority level, must be within [SubjectPriority.RANGE].
   * @return [Result.success] on success, or [Result.failure] with
   *   [TrackedSubjectError.INVALID_ARGUMENT] if [ownerId] is blank or contains a slash ('/'),
   *   [subject] is blank, [priority] is outside [SubjectPriority.RANGE], or [subject] is not
   *   tracked; nothing is written in that case.
   */
  suspend fun savePriority(ownerId: String, subject: String, priority: Int): Result<Unit>
}
