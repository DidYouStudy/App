// Co-authored-by: GitHub Copilot
package com.android.sample.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.sample.R
import com.android.sample.ui.theme.DysOrange
import com.android.sample.ui.theme.SampleAppTheme

object SignInScreenTestTags {
  const val SCREEN = "signIn_screen"
  const val APP_LOGO = "signIn_appLogo"
  const val TITLE = "signIn_title"
  const val GOOGLE_BUTTON = "signIn_googleButton"
  const val LOADING = "signIn_loading"
  const val ERROR = "signIn_error"
}

@Composable
fun SignInScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier.fillMaxSize().background(Color.White).semantics {
            this.testTag = SignInScreenTestTags.SCREEN
          },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
  ) {
    Spacer(modifier = Modifier.height(84.dp))

    Box(
        modifier =
            Modifier.size(189.dp)
                .border(2.dp, Color.Black, RoundedCornerShape(10.dp))
                .background(Color.White, RoundedCornerShape(10.dp))
                .semantics { this.testTag = SignInScreenTestTags.APP_LOGO },
    ) {
      Image(
          painter = painterResource(R.drawable.ic_launcher_foreground),
          contentDescription = stringResource(R.string.sign_in_logo_description),
          modifier = Modifier.align(Alignment.Center).size(150.dp),
      )
    }

    Spacer(modifier = Modifier.height(64.dp))

    Text(
        text = stringResource(R.string.sign_in_title),
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { this.testTag = SignInScreenTestTags.TITLE },
    )

    Spacer(modifier = Modifier.height(74.dp))

    OutlinedButton(
        onClick = onSignInClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(2.dp, DysOrange),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = DysOrange,
                disabledContainerColor = Color.White,
                disabledContentColor = DysOrange.copy(alpha = 0.6f),
            ),
        modifier =
            Modifier.size(width = 236.dp, height = 52.dp).semantics {
              this.testTag = SignInScreenTestTags.GOOGLE_BUTTON
            },
    ) {
      Text(
          text = stringResource(R.string.sign_in_google_button),
          fontSize = 15.sp,
          fontWeight = FontWeight.Medium,
      )
    }

    if (isLoading) {
      Spacer(modifier = Modifier.height(12.dp))
      CircularProgressIndicator(
          color = DysOrange,
          modifier = Modifier.size(24.dp).semantics { this.testTag = SignInScreenTestTags.LOADING },
      )
    }

    if (errorMessage != null) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
          text = errorMessage,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
          modifier = Modifier.semantics { this.testTag = SignInScreenTestTags.ERROR },
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun SignInScreenIdlePreview() {
  SampleAppTheme { SignInScreen(isLoading = false, errorMessage = null, onSignInClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun SignInScreenLoadingPreview() {
  SampleAppTheme { SignInScreen(isLoading = true, errorMessage = null, onSignInClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun SignInScreenErrorPreview() {
  SampleAppTheme {
    SignInScreen(isLoading = false, errorMessage = "Something went wrong", onSignInClick = {})
  }
}
