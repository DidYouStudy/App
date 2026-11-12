---
name: write-tests
description: Use when adding or changing code in this repo, to write or update its tests. Covers where tests live, what they must check, and the comment convention.
---

# Writing tests

## Where

- `app/src/test/` holds unit tests.
- `app/src/androidTest/` holds emulator tests, such as the Firestore repository. See the `instrumented-tests` skill to run them.

## Rules

- Every new class and function comes with unit tests.
- When you modify an existing class or function, find and update every test that uses it. Search for usages before you finish.
- Each test starts with a one-line comment stating the functionality it checks.
- Tests check actual behavior: outputs, state changes and interactions. Do not assert on implementation details.
- Cover more than the happy path: empty or missing input, error results and boundary values.
- A test should fail if the behavior it describes breaks. Check this by making the code wrong and watching it fail.
- ViewModel tests use fake repositories that implement the repository interface. ViewModels never depend on Firebase or a repository implementation, so their tests should not either.

## Example

```kotlin
// Verifies that an empty title is rejected and no event is saved.
@Test
fun saveEvent_emptyTitle_returnsErrorAndDoesNotSave() { ... }
```

## Before finishing

Run the tests you added or changed, then `./gradlew check`.
