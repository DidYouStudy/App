// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.android.sample.R
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing

/**
 * Dialog that lets the user change their username.
 *
 * It only hosts [EditUsernameDialogContent] in a dialog window; all changes go through callbacks.
 *
 * @param state Current content and status of the dialog.
 * @param onInputChange Called with the new text each time the user edits the field.
 * @param onSave Called when the user taps Save.
 * @param onDismiss Called when the user cancels or taps outside the dialog.
 */
@Composable
fun EditUsernameDialog(
    state: UsernameEditState,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    EditUsernameDialogContent(
        state = state,
        onInputChange = onInputChange,
        onSave = onSave,
        onDismiss = onDismiss,
    )
  }
}

/**
 * Content of the "Edit username" dialog: title, username field with its error under it, and the
 * Cancel and Save buttons.
 *
 * Save is enabled only when [UsernameEditState.canSave] is true, and the field and Cancel are
 * disabled while a save is running. Kept separate from the window so it can be tested on its own.
 *
 * @param state Current content and status of the dialog.
 * @param onInputChange Called with the new text each time the user edits the field.
 * @param onSave Called when the user taps Save.
 * @param onDismiss Called when the user taps Cancel.
 */
@Composable
internal fun EditUsernameDialogContent(
    state: UsernameEditState,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
  Surface(
      modifier = Modifier.testTag(SettingsScreenTestTags.EDIT_USERNAME_DIALOG),
      shape = RoundedCornerShape(Dimens.cornerMedium),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Column(modifier = Modifier.padding(Spacing.large)) {
      Text(
          text = stringResource(R.string.settings_username_dialog_title),
          style = MaterialTheme.typography.headlineSmall,
      )
      OutlinedTextField(
          value = state.input,
          onValueChange = onInputChange,
          modifier =
              Modifier.fillMaxWidth()
                  .padding(top = Spacing.medium)
                  .testTag(SettingsScreenTestTags.EDIT_USERNAME_FIELD),
          label = { Text(stringResource(R.string.settings_username_field_label)) },
          singleLine = true,
          enabled = !state.isSaving,
          isError = state.error != null,
          supportingText =
              state.error?.let { error ->
                {
                  Text(
                      text = stringResource(error.toMessageRes()),
                      modifier = Modifier.testTag(SettingsScreenTestTags.EDIT_USERNAME_ERROR),
                  )
                }
              },
      )
      Row(
          modifier = Modifier.fillMaxWidth().padding(top = Spacing.small),
          horizontalArrangement = Arrangement.End,
      ) {
        TextButton(
            onClick = onDismiss,
            enabled = !state.isSaving,
            modifier = Modifier.testTag(SettingsScreenTestTags.EDIT_USERNAME_CANCEL_BUTTON),
        ) {
          Text(stringResource(R.string.settings_username_cancel))
        }
        TextButton(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.testTag(SettingsScreenTestTags.EDIT_USERNAME_SAVE_BUTTON),
        ) {
          Text(stringResource(R.string.settings_username_save))
        }
      }
    }
  }
}
