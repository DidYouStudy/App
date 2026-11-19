// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

/**
 * Prepares a picked image for upload as a profile picture.
 *
 * ViewModels depend on this interface instead of `android.graphics`, so they can be tested with a
 * fake and never touch bitmaps.
 */
fun interface ImageCompressor {
  /**
   * Reads the image at [uri], shrinks it if it is large, and encodes it as JPEG.
   *
   * @param uri location of the picked image, as returned by the photo picker.
   * @return [Result.success] containing the JPEG bytes; [Result.failure] containing a
   *   [ProfilePictureException] with [ProfilePictureError.INVALID_IMAGE] when the image cannot be
   *   read or decoded.
   * @throws kotlinx.coroutines.CancellationException when the coroutine is cancelled. Cancellation
   *   is propagated rather than converted into a failed [Result].
   */
  suspend fun compress(uri: String): Result<ByteArray>
}
