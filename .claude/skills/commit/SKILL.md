---
name: commit
description: Use when committing changes in this repo. Covers the commit message format and type prefixes, what to stage, and the checks to run first.
---

# Committing

## Before committing

1. Run the tests you added or changed and make sure they pass.
2. If a test fails, read the error, fix the cause and rerun. Do not commit red tests.
3. Run `./gradlew ktfmtCheck`. Run the full `./gradlew check` before a PR; it is not needed for every local commit.

## Staging

- Stage only the files you changed, by explicit path.
- Never use `git add .` or `git add -A`. They can pull in local files such as `local.properties`, `.firebaserc` or emulator logs.
- Check `git status` and `git diff --staged` before committing.

## Message format

```
<type>: <Imperative subject>

<Optional body wrapped at 72 characters>
```

- The whole subject line, prefix included, is at most 50 characters.
- The subject is imperative with a capital first letter, e.g. `feat: Add user authentication`.
- Add a body when the subject alone does not explain the change. Write it in the imperative too, say what changed and why, and wrap it at 72 characters.
- Do not add a `Co-authored-by` line to the commit message. AI credit goes at the top of the files, see AGENTS.md.

## Types

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

Pick one type per commit. If a change needs two types, make two commits.
