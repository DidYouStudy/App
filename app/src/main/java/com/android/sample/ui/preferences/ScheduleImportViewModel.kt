// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.ui.preferences

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.event.EventsRepositoryProvider
import com.android.sample.model.event.ScheduleImporter
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Progress of a schedule import, shown to the user once it finishes. */
sealed interface ScheduleImportState {
  /** Nothing to report. */
  object Idle : ScheduleImportState

  /** An import is running. */
  object Importing : ScheduleImportState

  /** [count] events were imported; [skipped] malformed entries of the file were ignored. */
  data class Success(val count: Int, val skipped: Int = 0) : ScheduleImportState

  /** The import failed: no signed-in user, unreadable file, no valid event, or a storage error. */
  object Failure : ScheduleImportState
}

/** Runs schedule imports for the signed-in user; free of Android and Firebase types. */
class ScheduleImportViewModel(
    private val importer: ScheduleImporter,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _state = MutableStateFlow<ScheduleImportState>(ScheduleImportState.Idle)
  val state: StateFlow<ScheduleImportState> = _state.asStateFlow()

  /**
   * Imports the schedule opened by [openStream] into the signed-in user's calendar. Ignored while
   * another import is running.
   */
  fun importSchedule(openStream: () -> InputStream?) {
    if (_state.value == ScheduleImportState.Importing) return
    _state.value = ScheduleImportState.Importing
    viewModelScope.launch {
      val ownerId = authRepository.currentUser.first()?.uid
      _state.value =
          if (ownerId == null) ScheduleImportState.Failure
          else
              importer
                  .import(ownerId, openStream)
                  .fold(
                      onSuccess = { ScheduleImportState.Success(it.imported, it.skipped) },
                      onFailure = { ScheduleImportState.Failure },
                  )
    }
  }

  /** Resets to [ScheduleImportState.Idle] once the screen has shown the outcome. */
  fun consumeResult() {
    if (_state.value !is ScheduleImportState.Importing) _state.value = ScheduleImportState.Idle
  }
}

/** Creates [ScheduleImportViewModel] instances with the application's repositories. */
class ScheduleImportViewModelFactory(
    private val importer: ScheduleImporter,
    private val authRepository: AuthRepository,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass == ScheduleImportViewModel::class.java) {
      "Unsupported ViewModel: ${modelClass.name}"
    }
    @Suppress("UNCHECKED_CAST")
    return ScheduleImportViewModel(importer, authRepository) as T
  }
}

/** Returns a schedule import ViewModel backed by the application's repositories. */
@Composable
fun scheduleImportViewModel(): ScheduleImportViewModel =
    viewModel(
        factory =
            ScheduleImportViewModelFactory(
                ScheduleImporter(EventsRepositoryProvider.getRepository()),
                AuthRepositoryProvider.getRepository(
                    androidx.compose.ui.platform.LocalContext.current
                ),
            ),
    )
