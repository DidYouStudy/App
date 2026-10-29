// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.ui.sharedComponents

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
