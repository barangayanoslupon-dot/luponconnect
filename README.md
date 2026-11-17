# JEG Android app (APK)

GitHub builds the APK for you. You don't need Android Studio.

## What's inside
| Path | What it is |
|---|---|
| `www/index.html` | JEG himself (same as the web version) |
| `native/` | Android code: JEG's ears, voice, full screen, keep-awake |
| `scripts/patch-android.js` | Adds camera/mic permissions and landscape mode |
| `assets/` | App icon and splash screen (JEG's face) |
| `.github/workflows/build-apk.yml` | Tells GitHub how to build the APK |

## 1. Put your brain settings in
Open `www/index.html` and fill in the top, same as the web version:
```js
const JEG_CONFIG = {
  brainUrl: 'https://YOUR-PROJECT.supabase.co/functions/v1/jeg-ask',
  supabaseAnonKey: 'your-anon-or-publishable-key',
};
```

## 2. Upload to a **private** GitHub repo
1. GitHub → **New repository** → name it `jeg-app` → choose **Private** → Create.
2. **Add file → Upload files** → drag in everything from this folder, keeping the folders.
3. Check that `.github/workflows/build-apk.yml` was uploaded. Some computers hide folders that start with a dot.
   If it's missing: **Add file → Create new file**, type `.github/workflows/build-apk.yml` as the name, and paste the file's contents.
4. Click **Commit changes**.

## 3. Get the APK
1. Open the **Actions** tab. "Build JEG APK" starts by itself and takes about 5–8 minutes.
2. When it shows a green check, open it and scroll to **Artifacts** → download **JEG-apk** (a zip with `app-debug.apk`).
3. Every time you change a file and commit, GitHub builds a new APK.

## 4. Install on the Android phone
1. Copy `app-debug.apk` to the phone (Google Drive, USB or Messenger).
2. Tap it. If asked, allow **Install unknown apps** for that app (Drive/Files).
3. Open **JEG**, tap **Gisingin si JEG**, and allow **Camera** and **Microphone**.

## Notes
- JEG listens using Google's speech service. It needs internet, unless the phone has the offline English speech pack (Settings → Google → Voice → Offline speech recognition).
- To stop the "ding" each time JEG starts listening again, the app mutes notification and system sounds **while JEG is open**. They come back when you leave the app.
- The APK is a **debug** build, fine for testing and for your barangay clients. For the Play Store you'll need a signed release build later.
- The camera trackers download from the internet the first time JEG opens, then are cached.
