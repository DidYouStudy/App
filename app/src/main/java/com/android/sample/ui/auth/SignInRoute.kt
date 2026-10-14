// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Codex AI Agent
package com.android.sample.ui.auth

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SignInRoute(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = authViewModel(),
    onSignedIn: () -> Unit,
    getIdToken: suspend (Activity) -> String,
) {
  val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
  // React when the session-flow user becomes non-null, including restored sessions.
  // rememberUpdatedState keeps the latest callback.
  val currentOnSignedIn by rememberUpdatedState(onSignedIn)
  LaunchedEffect(uiState.user) { if (uiState.user != null) currentOnSignedIn() }
  val activity = LocalActivity.current

  SignInScreen(
      isLoading = uiState.isLoading,
      errorMessage = uiState.error?.let { stringResource(it.toMessageRes()) },
      onSignInClick = {
        activity?.let { currentActivity ->
          viewModel.signInWithGoogle { getIdToken(currentActivity) }
        }
      },
      modifier = modifier,
  )
}
