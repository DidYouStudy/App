// Co-authored-by: GitHub Copilot
package com.android.sample.ui.auth

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SignInScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun alwaysShowsScreenLogoTitleAndGoogleButton() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent {
      SignInScreen(isLoading = false, errorMessage = null, onSignInClick = {})
    }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.APP_LOGO).assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.sign_in_logo_description))
        .assertIsDisplayed()
        .assertContentDescriptionEquals(context.getString(R.string.sign_in_logo_description))
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.TITLE)
        .assertTextEquals(context.getString(R.string.sign_in_title))
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON)
        .assertTextEquals(context.getString(R.string.sign_in_google_button))
  }

  @Test
  fun idleButtonIsEnabledAndCallsCallbackOncePerClick() {
    var callbackCount = 0
    composeTestRule.setContent {
      SignInScreen(
          isLoading = false,
          errorMessage = null,
          onSignInClick = { callbackCount++ },
      )
    }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).performClick()
    org.junit.Assert.assertEquals(1, callbackCount)
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).performClick()
    org.junit.Assert.assertEquals(2, callbackCount)
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADING).assertDoesNotExist()
  }

  @Test
  @Config(qualifiers = "w640dp-h360dp")
  fun googleButtonCanBeReachedAndClickedOnShortLandscapeScreen() {
    var callbackCount = 0
    composeTestRule.setContent {
      SignInScreen(
          isLoading = false,
          errorMessage = null,
          onSignInClick = { callbackCount++ },
      )
    }

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
        .performClick()
    org.junit.Assert.assertEquals(1, callbackCount)
  }

  @Test
  fun clickingButtonWhileLoadingDoesNotTriggerCallback() {
    var callbackCount = 0
    composeTestRule.setContent {
      SignInScreen(
          isLoading = true,
          errorMessage = null,
          onSignInClick = { callbackCount++ },
      )
    }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).performClick()
    org.junit.Assert.assertEquals(0, callbackCount)
  }

  @Test
  fun showsErrorMessageExactlyAsProvided() {
    val errorMessage = "Sign-in failed"
    composeTestRule.setContent {
      SignInScreen(isLoading = false, errorMessage = errorMessage, onSignInClick = {})
    }

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.ERROR)
        .assertIsDisplayed()
        .assertTextEquals(errorMessage)
  }

  @Test
  fun doesNotShowErrorWhenMessageIsNull() {
    composeTestRule.setContent {
      SignInScreen(isLoading = false, errorMessage = null, onSignInClick = {})
    }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.ERROR).assertDoesNotExist()
  }

  @Test
  fun showsLoadingAndErrorAtTheSameTime() {
    val errorMessage = "Sign-in failed"
    composeTestRule.setContent {
      SignInScreen(isLoading = true, errorMessage = errorMessage, onSignInClick = {})
    }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADING).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.ERROR)
        .assertIsDisplayed()
        .assertTextEquals(errorMessage)
  }

  @Test
  fun changingFromLoadingToIdleRemovesLoadingAndEnablesButton() {
    val isLoading = mutableStateOf(true)
    composeTestRule.setContent {
      SignInScreen(isLoading = isLoading.value, errorMessage = null, onSignInClick = {})
    }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertIsNotEnabled()

    composeTestRule.runOnUiThread { isLoading.value = false }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertIsEnabled()
  }

  @Test
  fun changingErrorToNullRemovesError() {
    val errorMessage = mutableStateOf<String?>("Sign-in failed")
    composeTestRule.setContent {
      SignInScreen(isLoading = false, errorMessage = errorMessage.value, onSignInClick = {})
    }

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.ERROR)
        .assertIsDisplayed()
        .assertTextEquals("Sign-in failed")

    composeTestRule.runOnUiThread { errorMessage.value = null }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.ERROR).assertDoesNotExist()
  }
}
