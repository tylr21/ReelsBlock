# ReelsBlock

Personal-use Android app for limiting Instagram to ordinary Home posts, stories,
Search, profiles, and DMs. It covers positively identified ads, suggested posts
and inline reels, moves away from Reels browsing, and can hide Explore discovery.
A reel opened from a DM is allowed, with a structural single-reel lock.

**ReelsBlock is not affiliated with Instagram or Meta.** It is an experimental
personal tool, not a guaranteed content filter.

## Build on Windows

Open this existing project in Android Studio. Use its configured JDK and install
Android SDK Platform 37 and the required build tools. Set the SDK path in the
local, uncommitted `local.properties` file if Android Studio has not done so.
The app supports Android 8.0 / API 26 and later; device checks use Android 16.

From the project directory in PowerShell:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat test
```

APKs:

- Debug: `app\build\outputs\apk\debug\app-debug.apk`
- Release: `app\build\outputs\apk\release\app-release.apk`

The release build is deliberately **signed with this machine's Android debug
certificate** through `signingConfig = signingConfigs.getByName("debug")` in
`app\build.gradle.kts`. It is non-debuggable and ready for personal sideloading.
Debug and release builds on this machine share a certificate, so they can replace
each other without uninstalling or discarding DataStore preferences. Keep that
local keystore to preserve update compatibility. This is not a publishing setup;
use a private release key before distributing the app. See
[Android's release build documentation](https://developer.android.com/build/build-for-release).

## Sideload and enable ReelsGuard

Enable Developer options and USB debugging on the phone, connect it, and accept
the phone's USB debugging prompt. With Android SDK platform-tools on PATH:

```powershell
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
# Or install the personal-use release variant:
adb install -r .\app\build\outputs\apk\release\app-release.apk
```

Alternatively, transfer an APK to the phone and open it using a file manager;
Android may ask to allow that installer to install unknown apps.

1. Open **ReelsBlock**, review the accessibility disclosure, and choose
   **Agree and continue**.
2. In Android Settings, open **Accessibility → Downloaded/Installed apps →
   ReelsGuard** and enable the service. Menu names vary by phone.
3. If Android 13+ displays a restricted-settings warning, open **Settings → Apps →
   ReelsBlock → ⋮ → Allow restricted settings**, authenticate if asked, and return
   to Accessibility to enable ReelsGuard. The option can appear only after the
   blocked enable attempt. ADB installs may not need this extra step.
   These instructions follow
   [Android Help](https://support.google.com/android/answer/12623953?hl=en);
   service behavior has been checked on the Android 16 test phone.
4. Return to ReelsBlock. Its status reports whether Android has enabled ReelsGuard
   and whether the disclosure has been accepted. Both are needed for processing.
   **Review accessibility disclosure** remains available after setup.
5. Open Instagram to use the guard. To revoke access, disable ReelsGuard in
   Android Accessibility settings. ReelsBlock does not enable itself.

No separate “Display over other apps” or notification permission is needed.
The covers use accessibility overlay windows. Check the source and merged
manifests before installing: the app must never request INTERNET permission.

## Settings

All preferences and disclosure consent are stored locally in Preferences
DataStore. Changes apply to the connected service without restarting Instagram.
Service status is read from Android on resume; it is not a saved preference.

| Setting | Default | Effect |
| --- | --- | --- |
| Show toast | Off | Two-second, touch-through “ReelsGuard blocked content” feedback for a new cover or successful blocking navigation. It uses an accessibility window to stay above the covers. Repeated frames do not repeat it; feedback is limited to once per three seconds and cancelled when leaving Instagram. |
| Debug mode | Off | Enables redacted structural/action diagnostics under Logcat tag `ReelsGuard` in either variant. The app creates no log files. Only debug builds support the on-demand tree-dump receiver. |
| Auto-skip blocked items | Off | Requests one forward Home scroll per tracked blocked post, with a cooldown. It pauses when the end wall is visible. |
| Stop scrolling at end of followed content | On | Covers the captured end marker, Older posts, and everything below. Without a marker, a proven run of N blocked posts can establish the wall. Stories and ordinary posts above the wall stay outside it. A conservative backward action may reposition a boundary with an ordinary post above it. |
| N: consecutive blocked items before the wall | 3; minimum 1 | Number of distinct, consecutive positively blocked Home posts needed for the fallback wall. Unknown posts break the run; duplicate frames do not count again. The end marker establishes a wall independently of N. Controls are disabled while the end-wall switch is off. |
| Block Explore | On | Covers Explore pills/grid below Search and Saved and above the tab bar. Active Search removes the cover. Explore/search discovery viewers receive one BACK; Search profiles and their posts remain usable. |

There is no “Auto-switch to Following feed” setting.

## Privacy and permitted actions

There is no INTERNET permission or network code. Instagram text and content
descriptions are compared **in memory** only against fixed labels in
`ReelsRules.kt`. Snapshots retain structural values, fixed-label tags, and
framework node identity; captions, names, messages and queries are not retained.
App backup is disabled. The app writes only local settings, not diagnostic files.

Debug mode defaults off. When enabled, Logcat contains state names, resource IDs,
bounds, fixed-label booleans and action outcomes, never raw Instagram text or
descriptions. Exceptions are logged by class, without messages or stack traces.
On-demand structural dumps require a debug build, accepted disclosure, Debug mode,
an active Instagram root, and an ADB shell broadcast protected by Android's DUMP
permission. No app requests DUMP as a user permission.

Covers consume touches inside their rectangles and leave other areas usable.
The only automated navigation is Home/BACK or feed scrolling away from blocked
content. The service never likes, follows, comments, messages, or posts. Instagram
leaving the foreground clears all covers; interruption, unbinding and destruction
also clear them. See [the final audit](docs/final-audit.md) for checks and limits.

## Update rules after an Instagram change

1. Keep Instagram open on a non-DM screen and capture a fresh tree:

   ```powershell
   .\scripts\capture.bat explore_idle
   ```

   The script reads UI Automator XML into memory, removes all raw text and
   descriptions, and saves structural XML as `dumps\explore_idle.xml`. A failed
   idle capture is rejected; an old tree is never substituted. UI Automator can
   temporarily interrupt the accessibility service, so do not use such a capture
   to prove continuous tracking or settled overlays.

2. If UI Automator cannot idle, install the debug build and enable Debug mode,
   then use the service fallback:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\capture-service.ps1 -Name explore_idle
   adb logcat -d -s ReelsGuard
   ```

   The helper reads redacted logs into memory and saves only the derived
   structural XML, marked `source="ReelsGuard-service-fallback"`. It does not
   save diagnostic log files. Incomplete fallback trees are rejected.
   Capture names accept letters, numbers, underscores and hyphens.
3. Never open the DM inbox or a DM thread during capture work. If DM viewer
   evidence is needed, the phone owner must open that viewer manually. Do not
   navigate social controls. If locked or outside Instagram, stop capture work.
4. Compare IDs, visible bounds, selected tabs, hierarchy and fixed-label tags with
   [docs/rules-map.md](docs/rules-map.md). Do not infer visual order from tree order
   or invent resource IDs. Reject inverted, zero-area and off-screen nodes.
5. Edit Instagram IDs and fixed label strings **only in
   `app\src\main\java\com\example\reelsblock\ReelsRules.kt`**.
   Keep state transitions in `GuardStateMachine` and all windows in
   `OverlayManager`. Missing positive signals must allow content.
6. Add a sanitized fake-tree regression test, then run:

   ```powershell
   .\gradlew.bat test
   .\gradlew.bat assembleDebug
   ```

   Check manifests, sideload, and verify settled behavior with screenshots and
   redacted Logcat. Do not refresh the real feed for tests or perform social
   actions.

`dumps\` and `shots\` are local and gitignored. **Preserve both directories;
only the owner deletes them when explicitly requested.** Never commit captures.
Existing captures from earlier stages are retained; current helpers save
structural XML rather than raw UI content or diagnostic log files.

## Known limitations

- Instagram updates can break resource IDs, structure, and matching rules.
- Overlays can lag during fast flings, allowing transient content to appear.
- On the test phone, returning from ReelsBlock to Instagram can sometimes leave a
  black frame, despite live Search nodes remaining available. Opening Search and
  returning with Back redraws the screen. The rendering cause is unresolved;
  changing overlay hardware acceleration did not reliably fix it.
- Fixed labels depend on Instagram's app language. Current evidence uses English;
  changing language requires new captures and fixed-label updates.
- There is no positive “followed” signal in the captured tree. Absence of Follow
  is not proof of following; some recommended posts may slip through.
- Incomplete or uncertain snapshots fail open. Covers can briefly disappear when
  Instagram exposes unavailable/stale nodes.
- Accessibility services cannot disable a scroll gesture. An accessibility
  overlay can consume touches that begin inside it, but cannot cancel an already
  running fling. Navigation/scroll actions are best-effort.
- A feed post spans multiple rows. Continuity can become uncertain after a header
  leaves the screen; uncertain tails remain allowed.
- The DM lock uses captured hierarchy paths and available pager positions, not a
  permanent reel ID. Instagram changes or missing identity evidence can weaken
  detection; ambiguous identity fails open. Do not treat it as a security boundary.
