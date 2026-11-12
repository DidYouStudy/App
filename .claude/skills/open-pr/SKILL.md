---
name: open-pr
description: Use when deciding whether to open a pull request, or when writing one. Covers PR size guidance, the description, and the pre-PR checks.
---

# Opening a pull request

Opening a PR is visible to the whole team. Ask the developer before you open one.

## When a PR is warranted

- Open a PR for a **substantial, bounded** change.
- A PR over 100 changed lines counts as significant.
- Split a PR over 1000 changed lines into smaller, standalone PRs where possible.
- Do not open a PR for a trivial fix that belongs with other work.

## Before opening

- `./gradlew check` is green (unit tests and lint).
- `./gradlew ktfmtCheck` passes.
- If you touched code covered by `androidTest/`, run the instrumented tests too (see the `instrumented-tests` skill).
- Commits follow the `commit` skill.

## Description

State what changed and why, and the acceptance criteria it meets. Reviewers check the code against this text (see the `review-pr` skill), so keep it accurate.

Include:

- **Summary:** what changed and why, in a few sentences.
- **Acceptance criteria:** which ones this PR meets, as given in the task.
- **Testing:** which tests were added or changed, and what you ran.
- **Notes:** anything the reviewer should look at first, or known gaps.
