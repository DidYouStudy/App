---
name: instrumented-tests
description: Use when running or debugging the emulator tests under androidTest/, for example the Firestore repository tests. Covers the emulator setup and the exact commands.
---

# Instrumented tests

Features covered by tests under `app/src/androidTest/` must pass `connectedDebugAndroidTest` before you submit.

## Requirements

- A running Android emulator or connected device.
- The Firebase CLI (`firebase-tools`), with **Java 21+** available for the Firebase emulators. The Gradle daemon keeps its own Java 17 toolchain.
- Emulator config is in `firebase.json`: Auth on port 9099 and Firestore on port 8080.

## Run

```bash
./gradlew check ktfmtCheck
firebase emulators:exec --only auth,firestore --project demo-didyoustudy \
  './gradlew connectedDebugAndroidTest'
```

## Notes

- The tests use isolated, named Firebase apps pointing at `demo-didyoustudy` through Android's `10.0.2.2`. They never touch the real Firebase project.
- A debug-only network policy allows HTTP to the emulator host. Release policy is unchanged.
- Rerun these tests after Firebase SDK upgrades. They guard an assumption about SDK ordering.
- Never run these commands against the real project, and never deploy rules or indexes without the developer's say-so.
- See README.md for the full setup.
