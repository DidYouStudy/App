// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.model.location

import android.content.Context
import androidx.core.content.edit

/**
 * Persisted progression of the precise-location permission flow.
 *
 * Transitions are strictly monotonic from [INIT] toward [DENIED_TWICE] so closing the app or
 * returning to the screen never repeats earlier steps. The only transition back to [INIT] happens
 * when [GRANTED] is later revoked in system Settings.
 */
enum class PreciseLocationState {
  /** Initial state before prompting, or after [GRANTED] was revoked in system Settings. */
  INIT,

  /** First system prompt did not grant precise location; explanation dialog must be shown next. */
  RATIONALE_PENDING,

  /** Explanation dialog was dismissed; second system prompt must be launched next. */
  SECOND_PROMPT_PENDING,

  /** Both system prompts did not grant precise location; user must enable it in Settings. */
  DENIED_TWICE,

  /** Precise location (`ACCESS_FINE_LOCATION`) is granted. */
  GRANTED,
}

/** Side effect produced by [PreciseLocationStateMachine] transitions. */
enum class PreciseLocationAction {
  NONE,
  REQUEST_PERMISSION,
  EXPLAIN_RATIONALE,
  SEND_TO_SETTINGS,
}

/** Result of a state-machine transition: the next persisted [state] and the [action] to run. */
data class PreciseLocationTransition(
    val state: PreciseLocationState,
    val action: PreciseLocationAction,
)

/**
 * Pure, deterministic state machine governing precise-location permission prompts and dialogs.
 *
 * All transitions are side-effect-free and can be unit-tested without Android framework classes.
 */
object PreciseLocationStateMachine {

  /**
   * Reconciles [current] with the live OS [isFineGranted] permission status (e.g. on launch or
   * `ON_RESUME`).
   *
   * - If precise location is now granted, transitions to [PreciseLocationState.GRANTED].
   * - If [current] was [PreciseLocationState.GRANTED] and precise location is no longer granted,
   *   restarts the state machine at [PreciseLocationState.INIT].
   * - Otherwise preserves [current] so the flow never moves backward.
   */
  fun syncWithSystemPermission(
      current: PreciseLocationState,
      isFineGranted: Boolean,
  ): PreciseLocationState =
      when {
        isFineGranted -> PreciseLocationState.GRANTED
        current == PreciseLocationState.GRANTED -> PreciseLocationState.INIT
        else -> current
      }

  /**
   * Determines what to show when the user passively enters the map view.
   *
   * In [PreciseLocationState.DENIED_TWICE], passive map entry stays silent so switching views does
   * not block the map with a modal dialog.
   */
  fun onMapEntered(current: PreciseLocationState): PreciseLocationTransition =
      when (current) {
        PreciseLocationState.INIT ->
            PreciseLocationTransition(current, PreciseLocationAction.REQUEST_PERMISSION)
        PreciseLocationState.RATIONALE_PENDING ->
            PreciseLocationTransition(current, PreciseLocationAction.EXPLAIN_RATIONALE)
        PreciseLocationState.SECOND_PROMPT_PENDING ->
            PreciseLocationTransition(current, PreciseLocationAction.REQUEST_PERMISSION)
        PreciseLocationState.DENIED_TWICE,
        PreciseLocationState.GRANTED ->
            PreciseLocationTransition(current, PreciseLocationAction.NONE)
      }

  /**
   * Determines what to show when the user actively requests a precise-location feature (recenter
   * button or distance filter).
   */
  fun onLocationFeatureRequested(current: PreciseLocationState): PreciseLocationTransition =
      when (current) {
        PreciseLocationState.INIT ->
            PreciseLocationTransition(current, PreciseLocationAction.REQUEST_PERMISSION)
        PreciseLocationState.RATIONALE_PENDING ->
            PreciseLocationTransition(current, PreciseLocationAction.EXPLAIN_RATIONALE)
        PreciseLocationState.SECOND_PROMPT_PENDING ->
            PreciseLocationTransition(current, PreciseLocationAction.REQUEST_PERMISSION)
        PreciseLocationState.DENIED_TWICE ->
            PreciseLocationTransition(current, PreciseLocationAction.SEND_TO_SETTINGS)
        PreciseLocationState.GRANTED ->
            PreciseLocationTransition(current, PreciseLocationAction.NONE)
      }

  /**
   * Advances the state machine after the OS permission dialog returns [isFineGranted].
   *
   * Both coarse-only and complete denial are treated identically (`isFineGranted == false`). When
   * [suppressSettingsDialog] is true (e.g. after a post-Settings probe for "Ask every time"), a
   * denied result in [PreciseLocationState.DENIED_TWICE] stays silent instead of immediately
   * re-opening the Settings dialog.
   */
  fun onSystemPermissionResult(
      current: PreciseLocationState,
      isFineGranted: Boolean,
      suppressSettingsDialog: Boolean = false,
  ): PreciseLocationTransition {
    if (isFineGranted) {
      return PreciseLocationTransition(PreciseLocationState.GRANTED, PreciseLocationAction.NONE)
    }
    return when (current) {
      PreciseLocationState.INIT,
      PreciseLocationState.GRANTED,
      PreciseLocationState.RATIONALE_PENDING ->
          PreciseLocationTransition(
              PreciseLocationState.RATIONALE_PENDING,
              PreciseLocationAction.EXPLAIN_RATIONALE,
          )
      PreciseLocationState.SECOND_PROMPT_PENDING,
      PreciseLocationState.DENIED_TWICE ->
          PreciseLocationTransition(
              PreciseLocationState.DENIED_TWICE,
              if (suppressSettingsDialog) {
                PreciseLocationAction.NONE
              } else {
                PreciseLocationAction.SEND_TO_SETTINGS
              },
          )
    }
  }

  /**
   * Handles returning to the app after the user tapped "Open Settings".
   *
   * If [isFineGranted] is now true, transitions to [PreciseLocationState.GRANTED]. If still false
   * in [PreciseLocationState.DENIED_TWICE], launches a single OS permission probe so that selecting
   * "Ask every time" in Android Settings can show the one-time prompt and grant precise location.
   */
  fun onReturnedFromSettings(
      current: PreciseLocationState,
      isFineGranted: Boolean,
  ): PreciseLocationTransition {
    if (isFineGranted) {
      return PreciseLocationTransition(PreciseLocationState.GRANTED, PreciseLocationAction.NONE)
    }
    return if (current == PreciseLocationState.DENIED_TWICE) {
      PreciseLocationTransition(
          PreciseLocationState.DENIED_TWICE,
          PreciseLocationAction.REQUEST_PERMISSION,
      )
    } else {
      PreciseLocationTransition(current, PreciseLocationAction.NONE)
    }
  }

  /**
   * Advances the state machine when the user confirms or dismisses the first explanation dialog.
   */
  fun onExplanationDismissed(current: PreciseLocationState): PreciseLocationTransition =
      when (current) {
        PreciseLocationState.INIT,
        PreciseLocationState.RATIONALE_PENDING,
        PreciseLocationState.SECOND_PROMPT_PENDING ->
            PreciseLocationTransition(
                PreciseLocationState.SECOND_PROMPT_PENDING,
                PreciseLocationAction.REQUEST_PERMISSION,
            )
        PreciseLocationState.DENIED_TWICE ->
            PreciseLocationTransition(
                PreciseLocationState.DENIED_TWICE,
                PreciseLocationAction.SEND_TO_SETTINGS,
            )
        PreciseLocationState.GRANTED ->
            PreciseLocationTransition(PreciseLocationState.GRANTED, PreciseLocationAction.NONE)
      }
}

/** Persistence contract for [PreciseLocationState] across process restarts. */
interface PreciseLocationStateStore {
  /** Reads the persisted state, defaulting to [PreciseLocationState.INIT]. */
  fun getState(): PreciseLocationState

  /** Persists [state]. */
  fun setState(state: PreciseLocationState)

  /** Clears the persisted state back to [PreciseLocationState.INIT]. */
  fun reset()
}

/** [SharedPreferences]-backed implementation of [PreciseLocationStateStore]. */
class SharedPreferencesPreciseLocationStateStore(
    context: Context,
    prefsName: String = DEFAULT_PREFS_NAME,
) : PreciseLocationStateStore {
  private val sharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

  override fun getState(): PreciseLocationState {
    val raw =
        sharedPreferences.getString(KEY_PRECISE_LOCATION_STATE, null)
            ?: return PreciseLocationState.INIT
    return PreciseLocationState.entries.firstOrNull { it.name == raw } ?: PreciseLocationState.INIT
  }

  override fun setState(state: PreciseLocationState) {
    sharedPreferences.edit { putString(KEY_PRECISE_LOCATION_STATE, state.name) }
  }

  override fun reset() {
    sharedPreferences.edit { remove(KEY_PRECISE_LOCATION_STATE) }
  }

  companion object {
    const val DEFAULT_PREFS_NAME = "group_session_location_prefs"
    private const val KEY_PRECISE_LOCATION_STATE = "precise_location_state"
  }
}
