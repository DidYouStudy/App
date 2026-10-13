// Co-authored-by: GitHub Copilot
package com.android.sample.ui.auth

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.R
import com.android.sample.model.auth.GoogleSignInHelper

@Composable
fun SignInRoute(
    modifier: Modifier = Modifier,
    // TODO FIX VM INSTANCE NOT BEING ABLE TO BE CREATED AS DEFAULT PARAMETER
    viewModel: AuthViewModel = viewModel(),
    onSignedIn: () -> Unit,
    getIdToken: suspend (Activity) -> String = ::requestGoogleIdToken,
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

// Picker requests retain the prior Activity through rotation until close (rare, accepted).
/** Opens Google's account picker on [activity] and returns the ID token. */
suspend fun requestGoogleIdToken(activity: Activity): String =
    GoogleSignInHelper(CredentialManager.create(activity))
        .getIdToken(activity, activity.getString(R.string.default_web_client_id))
