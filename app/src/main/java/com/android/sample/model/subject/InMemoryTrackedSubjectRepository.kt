// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import com.android.sample.model.event.SubjectCatalogFirestoreMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * [TrackedSubjectRepository] kept in memory for ViewModel tests.
 *
 * Decisions, including saved priorities, live only as long as the instance.
 */
class InMemoryTrackedSubjectRepository : TrackedSubjectRepository {

  private val data = MutableStateFlow<Map<String, SubjectDecisions>>(emptyMap())

  override fun observeDecisions(ownerId: String): Flow<Result<SubjectDecisions>> {
    if (ownerId.isBlank() || '/' in ownerId) {
      return flowOf(Result.failure(TrackedSubjectException(TrackedSubjectError.INVALID_ARGUMENT)))
    }
    return data
        .map { byOwner -> Result.success(byOwner[ownerId] ?: SubjectDecisions()) }
        .distinctUntilChanged()
  }

  override suspend fun saveDecisions(
      ownerId: String,
      decisions: Map<String, Boolean>,
  ): Result<Unit> {
    if (ownerId.isBlank() || '/' in ownerId) {
      return Result.failure(TrackedSubjectException(TrackedSubjectError.INVALID_ARGUMENT))
    }
    if (decisions.isEmpty()) {
      return Result.success(Unit)
    }

    val seenKeys = mutableSetOf<String>()
    for (spelling in decisions.keys) {
      if (spelling.isBlank()) {
        return Result.failure(TrackedSubjectException(TrackedSubjectError.INVALID_ARGUMENT))
      }
      val key = SubjectCatalogFirestoreMapper.key(spelling)
      if (!seenKeys.add(key)) {
        return Result.failure(TrackedSubjectException(TrackedSubjectError.INVALID_ARGUMENT))
      }
    }

    data.update { byOwner ->
      val current = byOwner[ownerId]?.decisions.orEmpty()
      val updatedMap = current.toMutableMap()
      for ((spelling, tracked) in decisions) {
        val key = SubjectCatalogFirestoreMapper.key(spelling)
        // Keep any saved priority: changing the flag or spelling must not erase it.
        updatedMap[key] =
            SubjectDecision(
                spelling = spelling.trim(),
                tracked = tracked,
                priority = current[key]?.priority,
            )
      }
      byOwner + (ownerId to SubjectDecisions(updatedMap))
    }

    return Result.success(Unit)
  }

  override suspend fun savePriority(
      ownerId: String,
      subject: String,
      priority: Int,
  ): Result<Unit> {
    if (
        ownerId.isBlank() ||
            '/' in ownerId ||
            subject.isBlank() ||
            priority !in SubjectPriority.RANGE
    ) {
      return Result.failure(TrackedSubjectException(TrackedSubjectError.INVALID_ARGUMENT))
    }
    val validPriority = SubjectPriority(priority)
    val key = SubjectCatalogFirestoreMapper.key(subject)
    var accepted = false
    // The check and the write share one atomic update, so a concurrent untrack cannot slip between.
    data.update { byOwner ->
      val current = byOwner[ownerId]?.decisions.orEmpty()
      val entry = current[key]
      if (entry == null || !entry.tracked) {
        accepted = false
        byOwner
      } else {
        accepted = true
        val updated = entry.copy(priority = validPriority)
        byOwner + (ownerId to SubjectDecisions(current + (key to updated)))
      }
    }
    return if (accepted) {
      Result.success(Unit)
    } else {
      Result.failure(TrackedSubjectException(TrackedSubjectError.INVALID_ARGUMENT))
    }
  }
}
