// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography =
    Typography(
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            )
        /* Other default text styles to override
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
        */
    )

/**
 * Heading of a dashboard card. Material 3 has no 18sp style (titleMedium is 16sp, titleLarge is
 * 22sp), so this derives one from titleLarge with a matching line height. It is built once rather
 * than on every read; deriving it from [Typography] is safe because DidYouStudyTheme always uses
 * that object.
 */
val CardTitleTextStyle: TextStyle = Typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp)

/**
 * Label of the Google sign-in button. Like the Google brand colours, it is fixed by Google's
 * branding guidelines rather than by the app theme.
 */
val GoogleButtonTextStyle =
    TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.25.sp)
val calendarDateLabelStyle: TextStyle = TextStyle(fontSize = 10.sp)
val calendarEventTitleStyle: TextStyle =
    TextStyle(fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold)
val calendarEventSubjectStyle: TextStyle = TextStyle(fontSize = 9.sp, lineHeight = 11.sp)
val calendarEventTimeStyle: TextStyle = TextStyle(fontSize = 8.sp, lineHeight = 11.sp)

/** Initial drawn inside a small participant avatar of the group-session participants pill. */
val ParticipantInitialTextStyle: TextStyle = TextStyle(fontSize = 10.sp)

/** "+N" count of members without an avatar in the participants pill, slightly smaller. */
val ParticipantOverflowTextStyle: TextStyle = TextStyle(fontSize = 9.sp)
