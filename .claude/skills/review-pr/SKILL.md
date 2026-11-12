---
name: review-pr
description: Use when reviewing a pull request or a diff in this repo. A checklist for correctness against the PR message, MVVM consistency, test effectiveness and naming.
---

# Reviewing a pull request

Read the PR message first, then the diff.

## Checklist

1. **Matches the PR message.** Does the code do what the description and acceptance criteria say? Flag anything missing and anything extra.
2. **MVVM consistency.**
   - Data manipulation is in the ViewModel, not the screen.
   - ViewModels import no Firebase and no repository implementation. They depend on repository interfaces.
   - Firebase code lives only in `model/` repositories.
   - Nothing under `sigchecks/` or in generated code was edited.
   - The design fits the rest of the codebase.
3. **Tests are effective.**
   - New classes and functions have unit tests, and modified ones have updated tests.
   - Tests check actual behavior. A test that cannot fail is not a test.
   - Look for uncovered edge cases: empty input, error paths, boundaries.
   - Each test has the one-line comment naming the functionality it checks.
4. **No literals.** New strings are in `strings.xml`. Colors and sizes come from `ui/theme` tokens.
5. **Naming.** Classes and functions have sensible, concise names.
6. **Size.** A PR over 1000 lines should probably be split.

## Reporting

- Cite each finding as `file_path:line_number`.
- Lead with the most serious problems, and separate blocking issues from suggestions.
- Say what is good as well, so the author knows what to keep.
- The developer owns every line submitted. Review AI-written code as strictly as any other.
