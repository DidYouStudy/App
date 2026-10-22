// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.android.sample.R
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.GoogleButtonBorder
import com.android.sample.ui.theme.GoogleButtonText
import com.android.sample.ui.theme.Spacing

/** Test tags for the sign-in screen, shared by the UI and its tests. */
object SignInScreenTestTags {
  const val SCREEN = "signIn_screen"
  const val APP_LOGO = "signIn_appLogo"
  const val TITLE = "signIn_title"
  const val GOOGLE_BUTTON = "signIn_googleButton"
  const val GOOGLE_LOGO = "signIn_googleLogo"
  const val LOADING = "signIn_loading"
  const val ERROR = "signIn_error"
}

/** Displays sign-in branding and the Google sign-in action with loading and error feedback. */
@Composable
fun SignInScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // The opaque logo requires a white screen and black title until a transparent asset exists.
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .verticalScroll(rememberScrollState())
              .semantics { this.testTag = SignInScreenTestTags.SCREEN },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
  ) {
    Spacer(modifier = Modifier.height(Dimens.signInTopSpacing))

    // The unframed app logo provides the screen's visual identity.
    Image(
        painter = painterResource(R.drawable.app_logo),
        contentDescription = stringResource(R.string.sign_in_logo_description),
        contentScale = ContentScale.Fit,
        modifier =
            Modifier.size(Dimens.signInLogoSize).semantics {
              this.testTag = SignInScreenTestTags.APP_LOGO
            },
    )

    Spacer(modifier = Modifier.height(Dimens.signInLogoToTitleSpacing))

    // The bold black title introduces the sign-in action.
    Text(
        text = stringResource(R.string.sign_in_title),
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { this.testTag = SignInScreenTestTags.TITLE },
    )

    Spacer(modifier = Modifier.height(Dimens.signInTitleToButtonSpacing))

    // The Google-branded button stays visible but dims its contents while loading.
    Surface(
        onClick = onSignInClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(50),
        color = Color.White,
        contentColor = GoogleButtonText,
        border = BorderStroke(Dimens.borderThin, GoogleButtonBorder),
        modifier =
            Modifier.size(
                    width = Dimens.googleButtonWidth,
                    height = Dimens.googleButtonHeight,
                )
                .semantics { this.testTag = SignInScreenTestTags.GOOGLE_BUTTON },
    ) {
      val contentAlpha = if (isLoading) 0.38f else 1f
      Row(
          modifier =
              Modifier.fillMaxSize().padding(horizontal = Dimens.googleButtonHorizontalPadding),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Image(
            painter = painterResource(R.drawable.ic_google_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier.padding(end = Spacing.small)
                    .size(Dimens.googleLogoSize)
                    .alpha(contentAlpha)
                    .semantics { this.testTag = SignInScreenTestTags.GOOGLE_LOGO },
        )
        Text(
            text = stringResource(R.string.sign_in_google_button),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.25.sp,
            color = GoogleButtonText,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).alpha(contentAlpha),
        )
      }
    }

    // The themed progress indicator communicates the in-flight sign-in state.
    if (isLoading) {
      Spacer(modifier = Modifier.height(Spacing.medium))
      CircularProgressIndicator(
          color = MaterialTheme.colorScheme.primary,
          modifier =
              Modifier.size(Dimens.signInLoadingIndicatorSize).semantics {
                this.testTag = SignInScreenTestTags.LOADING
              },
      )
    }

    // The supplied error message remains visible below the sign-in action.
    if (errorMessage != null) {
      Spacer(modifier = Modifier.height(Spacing.small))
      Text(
          text = errorMessage,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
          modifier =
              Modifier.padding(horizontal = Dimens.signInErrorHorizontalPadding).semantics {
                this.testTag = SignInScreenTestTags.ERROR
              },
      )
    }
  }
}

/** Previews the sign-in screen before any action. */
@Preview(showBackground = true)
@Composable
internal fun SignInScreenIdlePreview() {
  DidYouStudyTheme { SignInScreen(isLoading = false, errorMessage = null, onSignInClick = {}) }
}

/** Previews the sign-in screen while authentication is loading. */
@Preview(showBackground = true)
@Composable
internal fun SignInScreenLoadingPreview() {
  DidYouStudyTheme { SignInScreen(isLoading = true, errorMessage = null, onSignInClick = {}) }
}

/** Previews the sign-in screen with an authentication error. */
@Preview(showBackground = true)
@Composable
internal fun SignInScreenErrorPreview() {
  DidYouStudyTheme {
    SignInScreen(isLoading = false, errorMessage = "Something went wrong", onSignInClick = {})
  }
}
