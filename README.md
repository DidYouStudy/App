<!-- Co-authored-by: OpenAI Codex -->
# App description

## Building locally
The app uses Firebase, and `app/google-services.json` is not committed. Download
it from the Firebase console (Project settings → Your apps → `com.android.sample`)
and place it in `app/` before building, otherwise the build fails at
`processDebugGoogleServices`. CI writes it from the `GOOGLE_SERVICES_JSON`
repository secret (the file, base64-encoded).

### Shared private debug signing

After pulling the signing change, teammates need two local files supplied by the
maintainer through the private team WhatsApp group:

1. Save the shared V2 keystore as **`app/private-debug.keystore`**.
2. Save the updated Firebase config as **`app/google-services.json`** (or download
   it from Firebase Project settings → Your apps → `com.android.sample`).

Both files are ignored by Git. The old tracked `app/debug.keystore` is removed by
this change. Gradle explicitly uses `app/private-debug.keystore` for debug builds;
it does not fall back to the old file or to `~/.android/debug.keystore`. The private
key must be present before installing or packaging debug APKs. Keep its alias
`androiddebugkey` and store/key passwords `android`. Never use it for release signing.

Run `./gradlew :app:signingReport` to confirm this SHA-1, then rebuild and test
Google sign-in:

```text
22:D4:A2:B7:E3:8E:85:1B:D3:75:FF:97:E8:D3:77:D5:7B:F9:3B:A6
```

If an old debug build is installed, switching certificates may require uninstalling
it first, which clears local app data. Renaming the V2 file does not change its
certificate. Teammates do not need to generate keys or register individual fingerprints.

#### Maintainer setup

1. Generate and back up one shared V2 key. Register its SHA-1/SHA-256 for
   `com.android.sample` in Firebase Project settings. Keep old fingerprints until
   teammates and CI confirm the new key works.
2. Configure the GitHub Actions repository secrets `DEBUG_KEYSTORE_BASE64` (the
   Base64-encoded V2 keystore) and `GOOGLE_SERVICES_JSON` (the updated config,
   Base64-encoded). CI restores and validates the key before building. A missing
   or malformed key secret fails the job; there is no legacy fallback.
3. Send the two files to teammates before merging so they can install them when
   they pull this change. Keep a separate restricted backup of the key.
4. Once teammates and CI confirm Google sign-in works, remove the old fingerprints
   from Firebase Project settings and verify the old Android OAuth client is gone.
   The old key remains recoverable from Git history. Downloading another JSON solely
   after retiring that old certificate is optional; its leftover OAuth entry does
   not prevent use of the new registered certificate.

On macOS, copy the key's Base64 contents into the GitHub secret field with:

```bash
base64 -i .local/signing/didyoustudy-debug-v2.keystore | tr -d '\n' | pbcopy
```

Copy the updated config for the other secret separately:

```bash
base64 -i /path/to/updated/google-services.json | tr -d '\n' | pbcopy
```

Base64 is encoding, not encryption. Do not put either secret value in logs or PRs.

### Firestore database and rules
The Firestore database itself is created once in the Firebase console (Build →
Firestore Database → Create database, ID `(default)`). Until it exists, the app
logs `NOT_FOUND: The database (default) does not exist` and Firestore stays in
offline mode, so every read fails.

Security rules live in `firestore.rules`, the source of truth: change them in a
PR, never in the console, since the next deploy overwrites console edits. With
the [Firebase CLI](https://firebase.google.com/docs/cli) (`npm install -g
firebase-tools`) and access to the project, deploy them with

```
firebase deploy --only firestore:rules --project <project-id>
```

where `<project-id>` is the `project_id` in `google-services.json`.
`firebase emulators:start` runs the Auth and Firestore emulators configured in
`firebase.json`, and the Firestore emulator enforces the same rules.

### Private event persistence

`EventsRepositoryFirestore` implements the existing `EventsRepository`; frontend can keep using
`InMemoryEventsRepository` in its tests. Production wiring is available through
`EventsRepositoryProvider.getRepository()`. Inject the interface into ViewModels; Firebase stays
in the model layer.

- Events: `users/{uid}/events/{eventId}`. The document ID is the event ID; the document stores
  `ownerId`, enum `type`, optional `subject`, UTC timestamps `startsAt`, `endsAt`, `createdAt`,
  `updatedAt`, optional `location`/`description`, and ordered `tasks` (`text`, `completedAt`).
- Suggestions: `users/{uid}/meta/subjects`, containing a `subjects` map. Keys are hex-encoded
  UTF-8 of the trimmed lowercase label; each entry stores `spelling` and `usedAt`.
  Each save batches the event and a merge of its one subject entry. Deletion retains suggestions.
- Live queries use `startsAt < to` and `endsAt > from`, ordered by start, end, and document ID.
  Matching malformed documents fail the read with `INVALID_EVENT_DATA`; they are not silently
  omitted. Missing optional fields become null or an empty task list. Records without query
  fields cannot be returned by Firestore's query; rules prevent new such records.
- Rules enforce ownership and top-level types, permitted fields, activity types, subject/location
  validation and duration. They do not enumerate every task or dynamic subject entry; the mapper
  validates those values when reading. This is deliberately scoped to private academic-project data.

#### Offline behavior and limits

Android Firestore disk persistence is enabled by default. Previously cached data can be read
without a network and survives client/app restart, subject to SDK cache eviction. Offline saves,
edits and deletes update the local cache and synchronize when reconnected. Reuse `newEventId()`'s
result when retrying a save. Events use last-write-wins; there is no conflict resolution or edit
history. Caller timestamps are stored as UTC, truncated to Firestore's microsecond precision.

Successful save/delete means **locally accepted**, not server-confirmed. The repository queues a
cache-only query after the write on the SDK's serial worker queue, then awaits that local query.
This avoids waiting for the server acknowledgement and covers idempotent deletes and repeated
identical saves. Emulator tests guard this SDK ordering assumption; rerun them after SDK upgrades.
Cancelling a coroutine does not retract a write already queued in Firestore.

The current interface intentionally does not expose pending/confirmed status, incomplete offline
ranges, or delayed server rejection notifications. An unvisited offline range may appear empty;
a later server rejection may roll back a locally displayed change. A single uncached document
read can fail with `STORAGE_FAILURE` rather than asserting that it does not exist. Cached reads
are still restricted to the current signed-in owner. No custom sync engine is used.

#### Running backend tests

Keep the existing in-memory/unit tests and run the separate real-SDK suite with an Android
emulator plus Firebase Auth and Firestore emulators (ports 9099 and 8080). Tests create isolated
named Firebase apps pointing only to `demo-didyoustudy`, accessed through Android's `10.0.2.2`.
A debug-only network policy permits HTTP to that emulator host; release policy is unchanged.

```bash
./gradlew check ktfmtCheck
firebase emulators:exec --only auth,firestore --project demo-didyoustudy \
  './gradlew connectedDebugAndroidTest'
```

Firebase emulators need Java 21+; the Gradle daemon uses its configured Java 17 toolchain.
The restart test recreates the SDK client with the same disk cache, including a queued write;
it does not simulate forcibly killing the Android process or clearing app data.

#### Publishing rules and indexes

In Firebase Console, open **Build → Firestore Database → Rules**, replace the editor contents
with the **entire repository `firestore.rules` file**, and click **Publish**. It includes the existing
profile/preferences permissions as well as event/subject permissions; do not paste only the new
matches. Keep any subsequent edits in this repository too.

Rules alone are insufficient for the overlap query. Prefer deploying both reviewed files with:

```bash
firebase deploy --only firestore:rules,firestore:indexes --project <project-id>
```

If configuring the index manually, create a **collection-scoped** composite index for collection ID
`events`, fields `startsAt` ascending and `endsAt` ascending (document ID ordering is implicit).
Wait for it to become enabled, then smoke-test the query against the project. The local emulator
does not prove that production indexes have been deployed. `firestore.indexes.json` also disables
unneeded single-field indexing for the subject catalog map and event tasks.

No manual collection creation, new sign-in provider, Cloud Function or Storage setup is required.
The parent `users/{uid}` document need not exist. Deployment is a maintainer step, not performed
by the test suite.

## App name
DidYouStudy

## Pitch
DidYouStudy is a study companion designed primarily for EPFL Bachelor students
who manage several concurrent courses with different rhythms: weekly exercises,
projects, deadlines, and exams that may still be months away.

While these students often know the individual tasks they need to complete,
keeping an accurate overview of their progress across courses and repeatedly 
deciding what deserves attention next can be mentally costly.
DidYouStudy addresses this problem by continuously connecting what the student 
planned, what they actually studied, how well it went, and what they should do 
next.

The app records evidence such as study-session duration, associated courses,
tasks or topics, task completion, deadlines, study goals, and optional
post-session feedback, then uses this information to maintain an evolving 
picture of the student's study and learning state across courses.

Instead of requiring the student to manually reconstruct their plan whenever 
reality changes, the app turns this state into concrete analytics and 
personalized suggestions about what to prioritize or schedule next, 
while keeping the student in control to accept, modify, postpone, or reject them.


The “state of a student” consists of a measure of two different factors:
the efficiency of the student evaluated by themselves while working on a 
certain topic (e.g. as a score from 1 to 5), and also whether the student 
has been completing the assignments for a certain topic. This state is computed 
for each individual topic in each subject, depending on the student’s 
performance, but also factors like the number of credits of that subject, 
all of them determining the amount of extra time that the student might need 
to spend on that subject.

For example, it would be possible to rank courses in terms of urgency, 
and the app would propose new studying sessions on the more urgent topics, 
in time slots not yet occupied. 
When a student first starts using the app, they will be prompted to import their 
course schedule as well as information about their study habits 
(preferred times, durations, etc.).

In addition, users are able to create study sessions with a specified topic 
and location, then allow other users to join. An overview of the open study 
sessions is available in a list in which different sessions are differently 
tagged. The app would automatically show, starting from the top of the list, 
the study sessions about the topics on which the user has struggled the most.

The core insight behind DidYouStudy is therefore that study planning and 
learning progress should not be treated as separate problems. The app can 
reduce the repeated mental effort of tracking, prioritizing and replanning 
while helping students answer three recurring questions: Where am I now? 
What should I focus on next? And how should my plan adapt to what actually 
happened?

If the scope is not too ambitious, another feature is: students can also 
provide course material they are struggling with, for example by taking a 
picture of an exercise or lecture content and receive AI-generated quizzes; 
their results can progressively provide additional evidence about which 
topics may need more attention without requiring the app to model or teach 
the entire course.

## Split-app model
The app relies on cloud services for authentication, data synchronization, 
and features that require external processing. Firebase Authentication is 
used to manage user accounts, while Firebase Firestore stores and synchronizes
user profiles, tasks, study sessions, and other study-related data.
Firebase Storage may be used for larger files such as photos or voice recordings.
Depending on the features implemented, additional cloud APIs may also be used 
for tasks such as speech-to-text or image processing, as well as an LLM API 
for more advanced features such as personalized feedback or quiz generation.

## Multi-user support
Users have individual accounts managed through Firebase Authentication, 
with their personal study data associated with their account and kept private 
by default. Users could optionally connect with friends, for example through 
usernames, invitation links, or QR codes, and choose which study statistics 
they want to share. The app could also support study groups, where students 
can set shared goals, compare progress, or organize study sessions together. 

More advanced features could eventually use users' study preferences and 
availability to suggest suitable group study sessions, while keeping detailed 
personal performance data private.

## Sensor use
### GPS:
For individual studying sessions: app records location of the study session. 
After the user completes a studying session at one location and gives their 
feedback, that feedback would be associated with the location, so that the app 
would generate reports about e.g. at which locations the user was the most 
productive. The data collected over the individual study sessions are not 
available to any other user.

For group studying sessions: app allows one user to share their location and 
claim a group studying session, with specified place, duration and topic. 
Throughout the duration of the studying session, it can be seen by other 
users on the app.

### Camera: 
The user takes photos of questions that were challenging, the app extracts 
the questions’ content and generates a quiz given the information from the 
photos. The user can also take a photo of a course schedule, syllabus, 
grading table or exam announcement. The app extracts information such as 
exam dates, project deadlines, grading weights, recurring weekly exercises or 
course events. It then shows the extracted fields for confirmation before adding them to the schedule/task.

## Offline mode
Without an internet connection, users can still access their study plan and 
learning history, create and edit tasks, track study sessions, and record 
feedback such as completion, difficulty, notes, or photos. Basic statistics 
and previously synchronized information also remain available offline.
Changes made offline are stored locally and synchronized once connectivity is 
restored. Features that depend on external services, such as advanced 
personalized recommendations, interactions with other users, 
or (if implemented) AI-generated quizzes, require an internet connection, 
although previously synchronized shared information can still be viewed 
offline where appropriate.

## Figma
For the design of the app, we have created a Figma project which includes mockups and tracks the design work. You can access it using this [link](https://www.figma.com/design/6Gggv7H9nuavBmqqvplxkD/DidYouStudy-?node-id=0-1&t=iAHZkCCt5bS0c0vp-1).

## Architecture Design
You can access the architecture design of the app in the same Figma project, or using the following [link](https://www.figma.com/design/6Gggv7H9nuavBmqqvplxkD/DidYouStudy-?node-id=127-449&t=cvg3umobGP4bajTg-1).
