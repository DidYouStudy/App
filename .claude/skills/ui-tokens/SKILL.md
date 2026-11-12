---
name: ui-tokens
description: Use when writing or editing screens, composables or any UI code in this repo. Covers the rule against string, color and size literals, and where the tokens live.
---

# UI tokens

Do not write string, color or size literals in UI code. Use a token.

## Strings

- User-visible text goes in `app/src/main/res/values/strings.xml` and is read from the string resource.
- Reuse an existing string when one already says the same thing.

## Colors and sizes

- Tokens live in `app/src/main/java/com/android/sample/ui/theme/`:
  - `Color.kt` for colors
  - `Dimens.kt` for sizes such as padding and font size
  - `Type.kt` for typography
  - `Theme.kt` for the theme itself
- Look for an existing token first.
- If none fits, add a new token with an appropriate name in that file. Do not inline the value.
- Colors in `res/values/colors.xml` follow the same rule: name them, do not hardcode them.

## Check before finishing

Search the files you changed for quoted text, `dp`, `sp` and `Color(0x`. Each hit should be a token, not a literal.
