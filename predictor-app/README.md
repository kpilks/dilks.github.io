# Prediction League (Android app)

A football prediction game for a group of friends:

- Each game week the admin picks **10 matches**. Players pick **Home / Draw / Away** for each one.
- **1 point** per correct result, and **+1** if their **banker** is right, so 11 points maximum.
- Picks can be changed until the deadline (the first kick-off by default). After that, everyone's picks become visible.
- Scores update **live** from ESPN's free scoreboard feed, and the league table updates with them.
- The admin **finalises** a week to lock its points into the season table.

Download page for players: **https://ccooper.co.uk/predictor/**

---

## One-off setup for the organiser (about 10 minutes)

The app keeps its shared data (accounts, picks, results) in a free **Firebase** project that you own.
The app downloads that project's settings from your website, so you **don't need a new APK** once it's set up.

### 1. Create the Firebase project
1. Go to <https://console.firebase.google.com> and sign in with a Google account.
2. **Create a project**, e.g. `prediction-league`. You can switch Google Analytics off.
3. Stay on the free **Spark** plan. It's plenty for a group of 100+ players.

### 2. Turn on email sign-in
**Build → Authentication → Get started → Sign-in method → Email/Password → Enable → Save.**

### 3. Create the database and paste in the security rules
1. **Build → Firestore Database → Create database.** Choose location `europe-west2 (London)` and start in **production mode**.
2. Open the **Rules** tab, delete what's there, paste the whole of [`firebase/firestore.rules`](firebase/firestore.rules), then click **Publish**.

The rules mean players can only edit their own picks before the deadline, can't see anyone else's picks until the deadline passes, and can't change points. Only admins can.

### 4. Register the Android app and get its settings file
1. Go to **Project settings** (gear icon) → **Your apps** → click the **Android** icon.
2. Android package name: **`uk.co.ccooper.predictor`**. Nickname is optional. Leave SHA-1 blank.
3. Click **Register app**, then **Download google-services.json**. Skip the remaining steps (the app is already built).

### 5. Publish the settings file on your website
Upload `google-services.json` to this repository as **`predictor/google-services.json`** on the branch GitHub Pages serves
(GitHub → *Add file → Upload files*). Check that it opens at
<https://ccooper.co.uk/predictor/google-services.json>.

> This file is safe to publish: it only says *which* Firebase project to use, and Firebase ships it inside every Android app anyway. Access is controlled by the security rules from step 3.

### 6. Become the admin
Install the app from <https://ccooper.co.uk/predictor/> and **register before anyone else**. **The first person to register becomes the admin.**
After that you'll see an **Admin** tab, where you can make other people admins too.

### 7. Invite everyone
Share <https://ccooper.co.uk/predictor/> (there's also a **Share the app** item in the app's menu).

---

## Running a game week (admin)

1. **Admin → New game week.** Choose the dates (◀ ▶ jumps a week) and competitions, then tap **Find fixtures** and tick 10 games.
2. Choose the **deadline**: at first kick-off, 30 minutes, 1 hour or 2 hours before it, or a custom date and time.
3. **Publish to players.** (Or **Save as draft** to finish later; drafts are hidden from players.)
4. During the matches, scores and the table update live. Your phone also saves the scores to the database.
5. When the matches are over, open the week and tap **Finalise week & award points**.
   - **Postponed or abandoned?** Set that match's result to **Void** so it doesn't count for anyone, then finalise.
   - **Need to correct a result?** Set it manually (Home / Draw / Away) and tap **Re-calculate points**.
6. **Submissions** shows who hasn't picked yet. You can also enter or fix picks for anyone, at any time.

**Players without the app (e.g. on iPhone):** Admin → **Add player without the app**. They appear in the table, and you enter their picks each week.

**Scoring notes:** predictions are on the **90-minute result**, so a match that goes to extra time or penalties counts as a **draw**.

---

## Building the app (developers)

Requirements: JDK 17+, Android SDK (platform 35).

```bash
./gradlew assembleRelease        # signed APK -> app/build/outputs/apk/release/
./gradlew testDebugUnitTest      # scoring + ESPN feed tests
./gradlew recordPaparazziDebug   # UI screenshots -> app/src/test/snapshots/
```

Release signing reads `keystore.properties` and `release.jks` from this folder. Neither is committed.
**Keep the keystore safe:** future updates must be signed with the same key, or players will have to uninstall before installing the update.
Their data is in Firebase, so reinstalling loses nothing.

The `e2e` build type is a shrunk build that reads its settings from `http://10.0.2.2:8000` and uses the local Firebase
emulators (any project ID starting `demo-`). It's used for end-to-end testing only.

### Firestore layout

| Path | Contents |
|---|---|
| `meta/admins` | `{ uids: [...] }`, claimed by the first person to register |
| `teamNames/{lowercase name}` | `{ uid, name }`, keeps team names unique |
| `users/{uid}` | `{ teamName, name, manual?, weekPoints: { weekId: points } }` |
| `weeks/{weekId}` | `{ title, deadline, status: draft/open/finalised, fixtures: [...] }` |
| `weeks/{weekId}/picks/{uid}` | `{ uid, picks: { fixtureId: H/D/A }, banker }` |
