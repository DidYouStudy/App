// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude AI Agent
package com.android.sample.ui.sharedComponents

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.sample.R
import com.android.sample.ui.theme.DidYouStudyTheme

/** Displays a reusable add-action floating action button. */
@Composable
fun AddButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
  FloatingActionButton(
      onClick = onClick,
      modifier = modifier,
  ) {
    Icon(
        imageVector = Icons.Default.Add,
        contentDescription = contentDescription,
    )
  }
}

/** Previews the add-action floating action button. */
@Preview(showBackground = true)
@Composable
fun AddButtonPreview() {
  DidYouStudyTheme {
    AddButton(
        onClick = {},
        contentDescription = stringResource(R.string.dashboard_add_description),
    )
  }
}
