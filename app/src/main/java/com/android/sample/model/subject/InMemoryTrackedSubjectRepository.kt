// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import com.android.sample.model.event.SubjectCatalogFirestoreMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** [TrackedSubjectRepository] kept in memory for ViewModel tests. */
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
        updatedMap[key] = SubjectDecision(spelling = spelling.trim(), tracked = tracked)
      }
      byOwner + (ownerId to SubjectDecisions(updatedMap))
    }

    return Result.success(Unit)
  }
}
