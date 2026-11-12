// Co-authored-by: Claude AI Agent
package com.android.sample.ui.sharedComponents

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.sample.R

/**
 * Shows a short toast telling the user that the feature they tapped is not implemented yet.
 *
 * Use it from non-Compose code (e.g. a callback that already has a [Context]). In a composable,
 * prefer [rememberNotImplementedToast].
 *
 * @param context Context used to resolve the message and display the toast.
 */
fun showNotImplementedToast(context: Context) {
  Toast.makeText(context, R.string.toast_not_implemented, Toast.LENGTH_SHORT).show()
}

/**
 * Returns a stable click handler that shows the "not implemented" toast.
 *
 * Plug it into any placeholder callback, for example:
 * ```
 * val notImplemented = rememberNotImplementedToast()
 * IconButton(onClick = notImplemented) { ... }
 * ```
 *
 * @return A lambda that shows the toast each time it is invoked.
 */
@Composable
fun rememberNotImplementedToast(): () -> Unit {
  val context = LocalContext.current
  return remember(context) { { showNotImplementedToast(context) } }
}
