// Co-authored-by: Copilot
package com.android.sample.model.profile

/**
 * Firestore profile document for a signed-in user.
 *
 * The profile currently stores only the authentication user ID. It is the persisted user-facing
 * model and can later contain data used to complete and personalize the user's profile.
 * [com.android.sample.model.auth.UserAccount] remains limited to authentication identity data.
 *
 * @param uid stable authentication identifier and Firestore document identifier.
 */
data class UserProfile(val uid: String = "")
