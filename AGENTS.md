# AGENTS.md

These are the standing rules for AI agents working in this repository. Read them before making changes. Documenting them also helps the team align on how we work.

## The app

This is a Kotlin/Android study planner app (`com.android.sample`) built using **MVVM** architecture.

- `model/` contains data and repositories for authentication, events, preferences, profiles, and related areas.
- `ui/` contains screens and their **ViewModels**, along with screen navigation actions and the tokens used by screens.

## Architecture rules

- Preserve the **MVVM** separation. Put all data manipulation in the ViewModel, never in the screen. **ViewModels must not import Firebase** or repository implementations; they must depend on repository interfaces. Keep Firebase code only in the `model/` repositories.
- Do not modify files in `sigchecks/` or generated code.

## Definition of done

- The feature works and satisfies its acceptance criteria set on the scrum board. 
- Before submitting, ensure `./gradlew check` passes (unit tests and lint) and `./gradlew ktfmtCheck` passes (formatting).
- For features covered by instrumented tests (in `androidTest/`, such as the Firestore repository), also pass `./gradlew connectedDebugAndroidTest` using a running Android emulator and Firebase emulator.

## Coding guidelines

- When mentioning specific code, include its file path and line number when possible.
- When generating code, add clear, suitable comments to every new class and function.
- Avoid functions with deeply nested statements; consider breaking them into smaller functions.
- Avoid hard-coded string, size, and color values. Put strings in `string.xml` and use their resources. For colors and numeric dimensions such as font and padding sizes, look for existing tokens in `ui/theme`; add appropriately named tokens when needed.
- **Credit contributors** at the top of each file by adding a `Co-authored-by` line for the AI that wrote it. AI agents count as contributors. Do not add `Co-authored-by` to commit messages. If the file already credits the agent, do not add another line.

## Tests

- **Write unit tests for every new class and function.**
- Store unit tests in `test/` and emulator tests in `androidTest/`.
- Ensure all tests are effective and cover a realistic case.
- Add a one-line comment to each test describing the functionality it checks.
- When modifying existing classes or functions, update every test that contains instances of them.

## Commits

- Use an imperative commit subject of no more than 50 characters. Start it with a type prefix, then an imperative description whose first letter is capitalized (for example, `Add user authentication`). Add a commit body when the subject alone does not describe the change sufficiently.
- Choose the type prefix that best describes the change:

| Type | Use for |
|---|---|
| `feat` | New feature or functionality |
| `fix` | Bug fix |
| `docs` | Documentation changes |
| `style` | Formatting or style changes, no behavior change |
| `refactor` | Restructuring code without changing behavior |
| `test` | Adding, updating or fixing tests |
| `chore` | Maintenance, dependencies, miscellaneous tasks |
| `build` | Build system or configuration changes |
| `ci` | CI/CD configuration or pipeline changes |
| `perf` | Performance improvements |
| `revert` | Reverting a previous change |

- Stage only the files you changed. Never run `git add .` or `git add -A`, since those commands may include local configuration such as `local.properties`.
- Before each commit, run the tests you added and confirm they pass. If tests fail, review the errors and iterate until `./gradlew check` passes.

## Pull requests

- Open a pull request for substantial, well-scoped changes. Changes exceeding 100 lines count as significant. When possible, split changes over 1000 lines into smaller, independent pull requests.

## Reviewing pull requests

- Confirm that the code works as described in the pull request message.
- Check that the design follows **MVVM** and is consistent with the rest of the codebase.
- Confirm that tests are effective, and consider whether they miss any edge cases.
- Check that class and function names are clear and concise.

## The developer's role

The developer sets the goal, context, acceptance criteria, and permissions. The agent plans, acts, and observes. **The developer reviews the diff and is responsible for every submitted line.** Saying "the agent wrote it" is not a defense.
