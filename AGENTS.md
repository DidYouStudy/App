# AGENTS.md

Durable rules for any AI agent working in this repository. Read this before acting.
Writing them down also helps the human team agree on how we build.

## The app

A Kotlin/Android Study planner app (`com.android.sample`), built with an **MVVM** architecture.

- `model/` holds the data and repositories (Authentication, events, preferences, profile...)
- `ui/` holds the screens and their **ViewModels**, as well as the screen navigation actions and the tokens to be used on screens. 

## Architecture rules

- Keep the **MVVM** separation. Data manipulation always happens in the ViewModel and not the screen itself. **ViewModels never import Firebase** or a repository implementation; they depend on repository interfaces. Firebase lives only in the `model/` repositories.
- Do not edit anything under `sigchecks/` or generated code.

## Definition of done

- The feature works and matches its acceptance criteria set on the scrum board.
- `./gradlew check` is green (unit tests + lint) and `./gradlew ktfmtCheck` passes (formatting) before you submit.
- Features covered by instrumented tests (under `androidTest/`, e.g. the Firestore repository) must also pass `./gradlew connectedDebugAndroidTest`, with an Android emulator and the Firebase emulator running.

## Coding Guidelines

- When referring to specific pieces of code , specify the line number and file path when possible.
- During code generation, add clear and appropriate comments to new classes and functions created.
- For functions that have multiple nested loops, add concise one-line comments to clarify the purpose of each loop. 
- Avoid functions with many nested loops, in which case consider splitting them up. 
- Restrain from using string/size/color literals. For string literals, add tokens in `string.xml` and use the string resource instead. For color and sizes (font and padding sizes), check in `ui/theme` for existing tokens to use, and add new tokens where needed. 
- - **Acknowledge your contributors** at the top of the file: credit the AI that wrote it with a `Co-authored-by` line. An AI agent is a contributor, so credit it. No need to mention `Co-authored-by` in commit message. If there exists already a line indicating credit for the agent, do not add a new one.  

## Test

- **All new classes and functions comes with unit tests.**
- All tests checks actual behaviors and 
- Add a one-line comment to the test that specifies the functionality being tested.
- When existing classes and functions are modified, modify all tests containing their instances. 

## Commits

- Commit with an imperative subject of at most 50 characters. The subject should have a prefix indicating capitalized (e.g. `Add user authentication`). Add a body wrapped at 72 characters when the subject for describing the changes.
- Stage only the files you changed; never `git add .` or `git add -A` (it can pull in local config like `local.properties`).
- Before each commit, run the tests being added and make sure they all pass. In case that tests are failing, examine the error messages and iterate until `./gradlew check` passes.

## Pull requests

- Open new pull requests when a **substantial, bounded** change was made. A PR over 100 lines counts as significant, whereas PRs over 1000 lines, if possible, should be split into smaller, standalone PRs.

## Reviewing pull requests

- Check the functionality of code against the changes specified in the PR message. 
- Ensure that the design is consistent with the **MVVM** concept and with the rest of the codebase. 
- Spot bad code smells. 
- Verify that all tests are effective. Consider any uncovered edge cases.
- Check that all classes and functions have sensible and concise names.

## The developer's role

The developer provides the goal, the context, the acceptance criteria and the permissions. The agent plans, acts and observes. **The developer review the diff, and owns every line submitted.** "The agent wrote it" is not a defense.