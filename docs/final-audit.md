# Stage 7 final audit

Audited 2026-11-06 against the existing Stage 2–6 capture evidence and the connected
Android 16 test phone. This is a personal-use app, not affiliated with Instagram
or Meta. No DM navigation or social actions were performed. Existing `dumps/` and
`shots/` were preserved; new local captures remain gitignored.

## Required audit results

| Requirement | Result and evidence |
| --- | --- |
| No INTERNET or unneeded permissions | **Pass.** Both debug and release merged manifests have exactly one `uses-permission`: `com.example.reelsblock.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. AndroidX supplies this app-private, signature-level guard for safe dynamic receivers; it grants no device/network capability. No INTERNET, network-state, storage, notification, or system-alert-window permission is requested. `BIND_ACCESSIBILITY_SERVICE` protects the service component; DUMP protects incoming debug broadcasts. Neither is requested as a `uses-permission`. Source scanning found no network code. |
| Instagram-only accessibility config | **Pass.** Source XML and the compiled release XML (`aapt2 dump xmltree`, resolved from `xml/reels_guard_service`) specify `packageNames="com.instagram.android"`, `canRetrieveWindowContent=true`, notification timeout 125 ms, and event mask `0x1820`: window-state changed, window-content changed, and view-scrolled only. The runtime event/package filter is also Instagram-only. Processing is sampled at 100 ms. |
| No Instagram text in logs; Debug mode gate; no disk logging | **Pass for current app/tooling.** The app's sole logging sink is `Log.d("ReelsGuard", message)`, behind accepted consent and the persisted Debug mode setting. Arguments contain structural values, fixed signal names/label booleans, preference values and action outcomes. Text/descriptions are compared only in memory against `ReelsRules.Label`; neither raw value enters a snapshot or log. Exception messages/stack traces are not logged. There are no app diagnostic-file writes; DataStore stores settings only. Debug builds alone expose the DUMP-protected structural dump receiver. The capture fallback was changed to keep Logcat output in process memory and save only requested derived structural XML, not `.service.txt` or `.diagnostics.txt` copies. Earlier explicitly exported capture logs remain untouched in the retained local directories; this audit does not claim historical files are absent. |
| No overlay windows over other apps | **Pass in the exercised lifecycle cases.** Post covers, the end wall, Explore cover, and toast feedback are owned by `OverlayManager`. Foreground loss clears covers/feedback on the next approximately 100 ms sample; interrupt, consent removal, unbind and destruction also clear them. A new `hasWindows` condition keeps cleanup polling alive if removal failed after the state machine reset. Lifecycle cleanup has a separate removal retry, and a stopping guard prevents new processing after unbind/destroy. Settled ReelsBlock screenshots and diagnostics show `overlays=0` after leaving Home post covers and Explore; the release toast and cover also disappear over ReelsBlock. This is sampled cleanup, not an instantaneous OS guarantee. |
| Working release build | **Build, signing and installation pass; UI caveat remains below.** `assembleRelease` succeeds, including release vital lint. `release.signingConfig` uses the existing local `debug` signing configuration. `apksigner verify --verbose` verifies APK Signature Scheme v2; the signer is Android Debug. Generated release `BuildConfig.DEBUG=false`; the merged release manifest has no `debuggable=true`. The APK installed with `adb install -r`, ran the guard and settings, and preserved DataStore preferences from debug. It is a personal debug-signed release, not a distribution-signing setup. |

The merged manifests are under
`app/build/intermediates/merged_manifests/{debug,release}/process{Debug,Release}Manifest/AndroidManifest.xml`.
The release artifact is `app/build/outputs/apk/release/app-release.apk`.
The signing choice is in `app/build.gradle.kts`; no key or password was committed.

## Settings wiring and phone checks

All six requested controls are present in both variants. No inactive setting or
Following-feed switch is shown. Status comes from Android on resume; disclosure
consent and all actual preferences are persisted through Preferences DataStore.

| Control | Runtime connection and verification |
| --- | --- |
| Show toast | DataStore → service feedback gate → `OverlayManager.showToast()`. New blocked surfaces/successful navigation trigger fixed feedback, at most once per three seconds; unchanged frames do not repeat it. Native system toasts were hidden behind the covers on this phone, so feedback is a non-focusable, non-touchable accessibility window. `stage7_release_toast_visible.png` shows the feedback; it expires after two seconds and is removed on foreground loss. The first toast/expiry screenshots also include early redraw frames; `stage7_release_settled_check.png` verifies the settled cover and bars separately. Show toast was restored off afterward. |
| Debug mode | DataStore → the single Logcat gate, effective in debug and release. Toggling it off, clearing Logcat, and returning to guarded Explore produced **zero ReelsGuard lines** while the cover remained visible (`stage7_explore_on_debug_off.png`). Turning it back on restored fixed diagnostics. The installed non-debuggable release also logged the opted-in structural/settings diagnostics. Tree-dump broadcasts remain debug-build-only. |
| Auto-skip blocked items | DataStore → `GuardStateMachine.step(autoSkip=...)` → rechecked Home `ACTION_SCROLL_FORWARD`. With the wall off, live logs recorded two distinct skip decisions followed by `Feed scroll success=true`. Turning the switch off reached the service immediately; it was restored off. `stage7_autoskip_home.png` is a moving/partial viewport, not settled proof that every ad tail is covered. |
| Stop scrolling at end of followed content | DataStore → `step(stopAtEnd=...)`, which disables both marker and run walls when false. Live diagnostics changed to `signal=disabled`; N controls became disabled in `stage7_wall_off_controls.xml`. The switch was restored on. The feed changed its loaded content before the intended same-marker screenshot, so `stage7_wall_off.png` is an ordinary-post screen, **not** direct evidence of uncovering that marker. Marker/run on/off behavior is covered by the retained Stage 5 tests and earlier Stage 5 phone evidence. |
| N | DataStore → `step(blockedGroupLimit=...)`. Changed 3 → 4 in the UI; service diagnostics confirmed `N=4`, and reopening settings retained it (`stage7_home_overlay_cleanup.png` also shows the value). Restored 4 → 3; the release update retained 3. Tests prove threshold behavior with captured fake trees and reject zero without overwriting a saved value. A fresh 3-versus-4 threshold comparison on live Home was not claimed. |
| Block Explore | DataStore → `step(blockExplore=...)`. Off exposes the live grid (`stage7_explore_off.png`, diagnostics `disabled; overlays=0`); on restores the cover below Search/Saved and above tabs (`stage7_explore_on_debug_off.png`, `stage7_release_settled_check.png`). Opening ReelsBlock removes the cover (`stage7_explore_overlay_cleanup.png`, release `stage7_release_no_overlay_leak.png`, diagnostics `overlays=0`). Restored on. |

`stage7_disclosure.png` verifies the reviewable, scrollable disclosure. Settings
captures expose stable Compose tags, checked/enabled state, and bounds; arbitrary
UI text is stripped in memory. The settings capture script accepts only the
Instagram or ReelsBlock package, with Instagram remaining the default. All tap
coordinates were calculated from captured element bounds.

Final installed variant: **release**. Final preferences: toast off, Auto-skip off,
end wall on, N=3, Block Explore on, Debug mode on (restored to the phone's earlier
opt-in value), disclosure accepted. Fresh-install Debug mode and toast defaults
are both off. The accessibility service remains enabled.

## Build/test evidence and limits

**Open rendering finding:** Returning from ReelsBlock to Instagram intermittently
left an almost entirely black frame, including the Search/bar text. This could
persist while idle, so it is not described as merely an early screenshot frame.
Live structural diagnostics still showed IDLE; tapping the previously captured
Search field successfully opened Search (`stage7_release_search.png`), and Back
restored the normal Explore cover and visible bars
(`stage7_release_idle_after_search.png`). A hardware-acceleration flag experiment
did not fix both return checks and was reverted. No root cause is established;
the issue remains documented in README. It does not change the observed
`overlays=0` cleanup over ReelsBlock, but limits the release UI verification.
`stage7_release_final.png` and `stage7_release_idle_later.png` illustrate the bad
frame and must not be used as proof of a normal settled screen. The normal settled
checks above remain evidence for the successful cases, not a claim of reliability
across every app switch.

- `gradlew.bat assembleDebug assembleRelease test`: passed; **55 unit tests, zero
  failures/errors**. Existing dump-based DM, feed, wall and Explore regressions
  still pass. Settings tests reopen DataStore and check every preference; the new
  count validation test checks that zero cannot overwrite a valid saved value.
- The release APK was verified and installed after inspecting the source and
  merged manifests for INTERNET and other permission requests. No uninstall,
  data clearing, key replacement, or service permission grant was needed.
- Android 13+ restricted-settings setup instructions were checked against
  [Android Help](https://support.google.com/android/answer/12623953?hl=en).
  This phone already had accessibility access enabled; a fresh APK-file install
  followed by the restricted-settings unlock was not repeated. ADB installs may
  not exercise that restriction.
- UI Automator temporarily disconnects accessibility tracking on this phone and
  can fail to idle. A moving Home fallback also rejected an incomplete tree.
  Failed captures were not reused as evidence. The fallback requires a debug
  build, so no service fallback was claimed for the final release build.
- Known functional limits remain: changing Instagram IDs/structure; English-label
  dependence; no positive followed-status signal; recommendations that lack
  positive blocked signals; incomplete snapshots failing open; lag during fast
  flings; and inability to disable an already running scroll gesture. DM identity
  remains structural, not a permanent media ID. These limits are in the README.
- No raw Instagram text was emitted by the current app or saved by the current
  capture helpers. No new diagnostic log files were exported during this stage.
  Old dumps, shots and previously exported logs were not deleted or committed.
