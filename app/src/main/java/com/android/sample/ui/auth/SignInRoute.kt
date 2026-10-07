// Co-authored-by: GitHub Copilot
package com.android.sample.ui.auth

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.R
import com.android.sample.model.auth.GoogleSignInHelper

@Composable
fun SignInRoute(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    getIdToken: suspend (Activity) -> String = ::requestGoogleIdToken,
) {
  val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
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

/** Opens Google's account picker on [activity] and returns the ID token. */
suspend fun requestGoogleIdToken(activity: Activity): String =
    GoogleSignInHelper(CredentialManager.create(activity))
        .getIdToken(activity, activity.getString(R.string.default_web_client_id))
