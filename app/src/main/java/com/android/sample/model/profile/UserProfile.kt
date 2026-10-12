// Co-authored-by: Copilot
package com.android.sample.model.profile

/**
 * Firestore profile document for a signed-in user.
 *
 * The profile stores user details such as authentication user ID, display username, and profile
 * picture URL. It is the persisted user-facing model and can later contain data used to complete
 * and personalize the user's profile. [com.android.sample.model.auth.UserAccount] remains limited
 * to authentication identity data.
 *
 * @param uid stable authentication identifier and Firestore document identifier.
 * @param username user-facing handle or display name.
 * @param profilePictureUrl URL pointing to the user's uploaded or chosen avatar photo.
 */
data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val profilePictureUrl: String? = null,
)
