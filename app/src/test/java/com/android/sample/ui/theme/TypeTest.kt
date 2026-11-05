// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Checks the app's custom text styles. */
class TypeTest {

  /** The card title is 18sp with a line height tall enough for wrapped lines not to overlap. */
  @Test
  fun cardTitleIs18spWith24spLineHeight() {
    val style = CardTitleTextStyle

    assertEquals(18.sp, style.fontSize)
    assertEquals(24.sp, style.lineHeight)
  }

  /** The card title is built once, so recomposing a card title does not allocate a new style. */
  @Test
  fun cardTitleIsTheSameInstanceOnEveryRead() {
    assertSame(CardTitleTextStyle, CardTitleTextStyle)
  }

  /** Apart from size, the card title follows titleLarge, so theme changes reach it too. */
  @Test
  fun cardTitleKeepsTitleLargeFamilyWeightAndSpacing() {
    val style = CardTitleTextStyle
    val base = Typography.titleLarge

    assertEquals(base.fontFamily, style.fontFamily)
    assertEquals(base.fontWeight, style.fontWeight)
    assertEquals(base.letterSpacing, style.letterSpacing)
  }

  /** The Google button label keeps the brand values it had before it was named. */
  @Test
  fun googleButtonTextStyleKeepsBrandValues() {
    assertEquals(14.sp, GoogleButtonTextStyle.fontSize)
    assertEquals(FontWeight.Medium, GoogleButtonTextStyle.fontWeight)
    assertEquals(0.25.sp, GoogleButtonTextStyle.letterSpacing)
  }
}
