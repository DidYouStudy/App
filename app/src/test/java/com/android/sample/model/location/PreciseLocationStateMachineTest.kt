// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.model.location

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for [PreciseLocationStateMachine] and [SharedPreferencesPreciseLocationStateStore].
 */
@RunWith(AndroidJUnit4::class)
class PreciseLocationStateMachineTest {

  @Test
  fun syncWithSystemPermission_grantsFromEveryState() {
    PreciseLocationState.entries.forEach { state ->
      assertEquals(
          PreciseLocationState.GRANTED,
          PreciseLocationStateMachine.syncWithSystemPermission(state, isFineGranted = true),
      )
    }
  }

  @Test
  fun syncWithSystemPermission_resetsOnlyWhenRevokedAfterGranted() {
    assertEquals(
        PreciseLocationState.INIT,
        PreciseLocationStateMachine.syncWithSystemPermission(
            PreciseLocationState.GRANTED,
            isFineGranted = false,
        ),
    )
    listOf(
            PreciseLocationState.INIT,
            PreciseLocationState.RATIONALE_PENDING,
            PreciseLocationState.SECOND_PROMPT_PENDING,
            PreciseLocationState.DENIED_TWICE,
        )
        .forEach { state ->
          assertEquals(
              state,
              PreciseLocationStateMachine.syncWithSystemPermission(state, isFineGranted = false),
          )
        }
  }

  @Test
  fun fullTwoRefusalProgression_isStrictlyMonotonic() {
    val onFirstMapEnter = PreciseLocationStateMachine.onMapEntered(PreciseLocationState.INIT)
    assertEquals(PreciseLocationState.INIT, onFirstMapEnter.state)
    assertEquals(PreciseLocationAction.REQUEST_PERMISSION, onFirstMapEnter.action)

    val firstRefusal =
        PreciseLocationStateMachine.onSystemPermissionResult(
            onFirstMapEnter.state,
            isFineGranted = false,
        )
    assertEquals(PreciseLocationState.RATIONALE_PENDING, firstRefusal.state)
    assertEquals(PreciseLocationAction.EXPLAIN_RATIONALE, firstRefusal.action)

    val afterExplanation = PreciseLocationStateMachine.onExplanationDismissed(firstRefusal.state)
    assertEquals(PreciseLocationState.SECOND_PROMPT_PENDING, afterExplanation.state)
    assertEquals(PreciseLocationAction.REQUEST_PERMISSION, afterExplanation.action)

    val secondRefusal =
        PreciseLocationStateMachine.onSystemPermissionResult(
            afterExplanation.state,
            isFineGranted = false,
        )
    assertEquals(PreciseLocationState.DENIED_TWICE, secondRefusal.state)
    assertEquals(PreciseLocationAction.SEND_TO_SETTINGS, secondRefusal.action)
  }

  @Test
  fun permissionGrantedOnFirstOrSecondPrompt_transitionsToGranted() {
    val firstGrant =
        PreciseLocationStateMachine.onSystemPermissionResult(
            PreciseLocationState.INIT,
            isFineGranted = true,
        )
    assertEquals(PreciseLocationState.GRANTED, firstGrant.state)
    assertEquals(PreciseLocationAction.NONE, firstGrant.action)

    val secondGrant =
        PreciseLocationStateMachine.onSystemPermissionResult(
            PreciseLocationState.SECOND_PROMPT_PENDING,
            isFineGranted = true,
        )
    assertEquals(PreciseLocationState.GRANTED, secondGrant.state)
    assertEquals(PreciseLocationAction.NONE, secondGrant.action)
  }

  @Test
  fun passiveMapEntryVsActiveFeatureRequest_differsOnlyInDeniedTwice() {
    assertEquals(
        PreciseLocationAction.NONE,
        PreciseLocationStateMachine.onMapEntered(PreciseLocationState.DENIED_TWICE).action,
    )
    assertEquals(
        PreciseLocationAction.SEND_TO_SETTINGS,
        PreciseLocationStateMachine.onLocationFeatureRequested(PreciseLocationState.DENIED_TWICE)
            .action,
    )

    assertEquals(
        PreciseLocationAction.EXPLAIN_RATIONALE,
        PreciseLocationStateMachine.onMapEntered(PreciseLocationState.RATIONALE_PENDING).action,
    )
    assertEquals(
        PreciseLocationAction.REQUEST_PERMISSION,
        PreciseLocationStateMachine.onMapEntered(PreciseLocationState.SECOND_PROMPT_PENDING).action,
    )
    assertEquals(
        PreciseLocationAction.NONE,
        PreciseLocationStateMachine.onMapEntered(PreciseLocationState.GRANTED).action,
    )
    assertEquals(
        PreciseLocationAction.EXPLAIN_RATIONALE,
        PreciseLocationStateMachine.onLocationFeatureRequested(
                PreciseLocationState.RATIONALE_PENDING
            )
            .action,
    )
    assertEquals(
        PreciseLocationAction.REQUEST_PERMISSION,
        PreciseLocationStateMachine.onLocationFeatureRequested(
                PreciseLocationState.SECOND_PROMPT_PENDING
            )
            .action,
    )
    assertEquals(
        PreciseLocationAction.NONE,
        PreciseLocationStateMachine.onLocationFeatureRequested(PreciseLocationState.GRANTED).action,
    )
  }

  @Test
  fun onExplanationDismissed_respectsMonotonicityInTerminalStates() {
    val fromDeniedTwice =
        PreciseLocationStateMachine.onExplanationDismissed(PreciseLocationState.DENIED_TWICE)
    assertEquals(PreciseLocationState.DENIED_TWICE, fromDeniedTwice.state)
    assertEquals(PreciseLocationAction.SEND_TO_SETTINGS, fromDeniedTwice.action)

    val fromGranted =
        PreciseLocationStateMachine.onExplanationDismissed(PreciseLocationState.GRANTED)
    assertEquals(PreciseLocationState.GRANTED, fromGranted.state)
    assertEquals(PreciseLocationAction.NONE, fromGranted.action)
  }

  @Test
  fun onReturnedFromSettings_probesSystemPromptWhenStillDeniedTwice() {
    val stillDenied =
        PreciseLocationStateMachine.onReturnedFromSettings(
            PreciseLocationState.DENIED_TWICE,
            isFineGranted = false,
        )
    assertEquals(PreciseLocationState.DENIED_TWICE, stillDenied.state)
    assertEquals(PreciseLocationAction.REQUEST_PERMISSION, stillDenied.action)

    val probeDenied =
        PreciseLocationStateMachine.onSystemPermissionResult(
            stillDenied.state,
            isFineGranted = false,
            suppressSettingsDialog = true,
        )
    assertEquals(PreciseLocationState.DENIED_TWICE, probeDenied.state)
    assertEquals(PreciseLocationAction.NONE, probeDenied.action)

    val probeGranted =
        PreciseLocationStateMachine.onSystemPermissionResult(
            stillDenied.state,
            isFineGranted = true,
            suppressSettingsDialog = true,
        )
    assertEquals(PreciseLocationState.GRANTED, probeGranted.state)
    assertEquals(PreciseLocationAction.NONE, probeGranted.action)

    val grantedInSettings =
        PreciseLocationStateMachine.onReturnedFromSettings(
            PreciseLocationState.DENIED_TWICE,
            isFineGranted = true,
        )
    assertEquals(PreciseLocationState.GRANTED, grantedInSettings.state)
    assertEquals(PreciseLocationAction.NONE, grantedInSettings.action)

    val nonDeniedTwiceUnchanged =
        PreciseLocationStateMachine.onReturnedFromSettings(
            PreciseLocationState.RATIONALE_PENDING,
            isFineGranted = false,
        )
    assertEquals(PreciseLocationState.RATIONALE_PENDING, nonDeniedTwiceUnchanged.state)
    assertEquals(PreciseLocationAction.NONE, nonDeniedTwiceUnchanged.action)
  }

  @Test
  fun sharedPreferencesStore_persistsAcrossInstancesAndResetsToInit() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefsName = "test_precise_location_prefs"
    val store1 = SharedPreferencesPreciseLocationStateStore(context, prefsName)
    assertEquals(PreciseLocationState.INIT, store1.getState())

    PreciseLocationState.entries.forEach { state ->
      store1.setState(state)
      val store2 = SharedPreferencesPreciseLocationStateStore(context, prefsName)
      assertEquals(state, store2.getState())
    }

    store1.reset()
    assertEquals(
        PreciseLocationState.INIT,
        SharedPreferencesPreciseLocationStateStore(context, prefsName).getState(),
    )
  }
}
