# Stage 2: Instagram capture evidence

Captured on 2026-11-06 from the connected test phone at **1440 x 3168**.
This stage changes documentation only. The rules below are implementation candidates,
not implemented or verified blocking behavior. No app was installed.

## Capture format and provenance

- Each state below has `dumps/<state>.xml` and `shots/<state>.png`, unless explicitly
  listed as missing or reconnaissance only. Captures remain local and gitignored;
  retain them until the user says the project is finished.
- Every captured requested state was attempted with `scripts\capture.bat`. UI Automator
  succeeded except on Explore idle and the DM shared-reel viewer. Explore return
  captures also needed the fallback.
- On those failures, UI Automator printed `ERROR: could not get idle state.` but
  returned success; the script pulled an old `/sdcard/ui.xml`. Those files are
  retained as `<state>.uiautomator-stale.xml` and **must not support any rule**.
- Fallback: `adb logcat -c`, then
  `adb shell am broadcast -a com.example.reelsblock.DUMP -p com.example.reelsblock`,
  then `adb logcat -d -s ReelsGuard`. The complete redacted tree is saved as
  `<state>.service.txt`. Its path hierarchy, resource IDs, classes, selected states,
  bounds, and fixed-label booleans are transcribed into `<state>.xml`, explicitly
  marked `source="ReelsGuard-service-fallback"`. These are derived XML files,
  **not UI Automator dumps**. No clickable, focused, or raw text fields are inferred.
- Fallback trees completed with `unavailableChildren=0 failedNodes=0`: Explore idle
  visited 135 nodes, Explore return/after-viewer 136 each, and DM shared reel 101.
- Screenshots were taken immediately after each successful tree capture, without
  intervening navigation. Videos and dynamic labels can change between the two.
- IDs below omit the observed `com.instagram.android:id/` prefix; `android:id/list`
  is an Android ID. Only fixed interface labels appear in this document.

## Requested states and supported rules

| Dump / screenshot stem | Source | Observed signals and candidate rule |
| --- | --- | --- |
| `home_followed_post` | UI Automator | Home `feed_tab` selected; `android:id/list` at `[0,160][1440,2800]`. `row_feed_profile_header` at `[0,160][1440,368]`, `carousel_video_media_group` / `carousel_video_image` at `[0,368][1440,2285]`, and `row_feed_view_group_buttons` at `[0,2285][1440,2549]`. Ordinary post reference: preserve when no positive blocked-content signal exists. Following status is not positively exposed; absence of a Follow button alone does not prove it. |
| `home_followed_post_next_header` | UI Automator | Continuation of that post; its media ends at y=1923 and actions at y=2187. Next `row_feed_profile_header` is `[0,2516][1440,2724]`, with `secondary_label` = `Suggested for you` and `inline_follow_button` = `Follow`. Supports grouping consecutive feed rows by geometry and keeping the upper post separate from the suggested post. The upper post's header has scrolled off-screen. |
| `home_sponsored_post` | UI Automator | Explicit `Ad` in `secondary_label` at `[196,410][963,518]`, inside `row_feed_profile_header` at `[0,310][1440,518]`; media `[0,518][1440,2318]`, `cta_container` `[0,2318][1440,2494]`, and actions `[0,2494][1440,2678]`. Header, creative, CTA, and actions fit in the viewport. Supports blocking the geometrically associated ad rows, not the entire feed. Caption/footer completeness is not established. |
| `home_end_of_feed` | UI Automator | User manually exposed the marker. Selected Home, `end_of_feed_demarcator_container` `[0,902][1440,1316]`, `demarcator_bar_container` `[0,966][1392,1100]`, `demarcator_icon`, and `subtitle`; `title` = `Suggested for you`, `link` = `Older posts` at `[1007,1150][1440,1278]`. Suggested post starts at y=1316. Supports the end-of-followed-feed boundary. Stage 5 explicitly covers Older posts along with the marker and everything below it. The subtitle does not exactly match either configured caught-up phrase, so that label alone would miss this state. Stories occupy the region immediately above the marker. |
| `home_end_of_feed_with_followed_posts` | **Missing (optional)** | This marker directly follows the stories tray; the loaded feed exposes no ordinary posts above it. Must verify a mixed boundary before covering rows below a marker while preserving a post above it. |
| `explore_idle` | Service fallback | Selected `search_tab` at `[864,2800][1152,2992]`; `explore_action_bar` `[0,160][1440,384]`, `action_bar_search_edit_text` `[48,180][1248,356]` with `lbl-SEARCH=true`, and `recycler_view` containing grid `image_button` / `image_preview` nodes. Supports recognizing the Explore grid while preserving an intentional Search entry point. Do not classify from the generic recycler ID alone. |
| `explore_search_focused` | UI Automator | Before query entry, `action_bar_search_edit_text` is `android.widget.EditText`, focused and focusable, at `[224,188][1360,364]`; `action_bar_button_back` and recent-search `search_section_container` / `row_search_user_container` appear. Its XML text field is nonempty even before typing, so do not infer a user query from nonempty text alone. Supports allowing active Search instead of treating every search-related screen as Explore. Keyboard visibility is not established by the tree. |
| `explore_search_typed` | UI Automator | Same edit field at `[224,188][1248,364]` after entering a neutral query, with `search_keyword_title`, `row_search_keyword_title`, and search-user suggestions. No result tab bar yet. Supports the typed-search state without retaining or matching arbitrary query/account text. |
| `search_results` | UI Automator | `tabbed_pager`, `search_tab_bar_layout`, `fixed_tabbar_tabs_container`, and `tab_button_name_text`: `For you` selected, `Accounts`, `Audio`, `Tags` visible at y=436..508. Supports distinguishing submitted results from Explore idle and autocomplete. A Follow button exists inside a search result; it must not trigger the Home suggested-post rule. |
| `explore_post_viewer` | UI Automator | Entered via grid `image_button` bounds `[482,1409][958,2044]`, tap `(720,1726)`. `root_clips_layout`, `clips_viewer_view_pager`, and `clips_single_media_component` are present; `clips_viewer_action_bar_title` = `Explore` at `[256,160][1216,384]`. No bottom navigation tab bar; footer has `comment_composer_text_view`. Supports blocking a reel opened from Explore using origin/title context. A non-reel photo viewer remains untested. |
| `home_inline_reel` | UI Automator | Home `feed_tab/tab_icon` selected; `video_container` at `[0,902][1440,2800]`, `feed_preview_keep_watching_backdrop`, `feed_preview_keep_watching_button` `[366,1922][1075,2114]`, and `feed_preview_watch_again_text`. Supports detecting an inline reel preview inside Home; generic video nodes alone do not establish a reel. The keep-watching control was never tapped. |
| `reels_tab` | UI Automator | `clips_tab` selected at `[288,2800][576,2992]`; `root_clips_layout`, `clips_viewer_view_pager`, `clips_single_media_component`, and `action_bar_tab_layout`; selected `android:id/text1` = `Reels`. Supports blocking the dedicated Reels entry point. Immediately pressed Back after capture; `reels_return` confirms return to Home. |
| `dm_shared_reel` | Service fallback | User manually opened the shared-reel viewer; agent performed no DM navigation. Shared clips structure plus `sender_profile_pic`, `sender_username_or_fullname` `[382,208][561,290]`, `sender_timestamp`, `reply_bar_container_and_title` `[0,2752][1440,2992]`, `reel_viewer_message_composer`, and `reply_bar_edittext`. Supports a candidate DM-origin exception using structural context; no sender/message content was retained in the service dump. A single snapshot does not prove a safe one-reel lock or establish reliable reel identity. |

## Additional retained evidence

| Dump stem(s) | Purpose / supported observation |
| --- | --- |
| `initial_state` | Initial foreground check: Instagram package, Home, and inline keep-watching controls. Reconnaissance XML only; use the paired `home_inline_reel` capture for rules. |
| `home_scan_01` | Lower portion of the first inline reel, its action rows, and the next post header: supports multi-row feed grouping. |
| `home_scan_02` | Full suggested-video media `[0,344][1440,2576]`, header and actions; `secondary_label` = `Suggested for you` and `inline_follow_button` = `Follow`. That secondary label later changed to a nonmatching label without scrolling, so its absence cannot safely establish followed content. |
| `home_scan_03` | Partial previous video above an explicit `Ad` header at `[0,1007][1440,1215]`; corroborates ad detection and boundaries. |
| `home_scan_04` | Ad tail above another `Suggested for you` header at `[0,844][1440,1052]`; supports separate post grouping. |
| `home_scan_05` | Ordinary-post action tail above a `Suggested for you` header at `[0,562][1440,770]`; no caught-up marker. |
| `home_scan_06`, `home_scan_07`, `home_scan_08` | Further Home positions, including inline video and suggested headers. No end marker found; these do not establish that the feed has ended. |
| `explore_return`, `explore_after_viewer` | Service fallback confirmations of Explore grid after Back from search results and from its reel viewer. Corroborate idle Explore and selected Search tab; include paired screenshots and redacted logs. |
| `reels_return` | UI Automator confirmation that Back from the Reels tab returned to selected Home with an ordinary carousel post. |
| `explore_idle.uiautomator-stale`, `explore_return.uiautomator-stale`, `explore_after_viewer.uiautomator-stale`, `dm_shared_reel.uiautomator-stale` | Failed UI Automator attempts containing previous-screen XML. Invalid as evidence for the filename's state; no corresponding screenshot is claimed. |

## Constraints for the implementation stage

- Use bounds and visible screen context, not traversal order. The Explore fallback
  includes old Home `android:id/list` nodes with bounds such as
  `[0,160][-2880,2800]`. Reject inverted/zero-area or off-screen bounds before
  selecting a visible surface or applying a rule.
- A selected tab may be represented on the tab or its `tab_icon` child. Presence
  of all tab IDs alone says nothing about the active tab.
- Build feed groups from header, media, CTA, actions, and footer rows with spatial
  boundaries. A suggested header below an allowed post must not classify the upper
  post as blocked. Do not assume the post has one common container.
- Scope fixed labels to their screen and row role. Follow controls occur in Home,
  search results, Explore reels, and DM reels; none is a standalone block signal.
- Clips-viewer IDs are shared by Explore, Reels, and DM. Any future state machine
  must consider the DM exception before a generic viewer rule. These dumps do not
  validate scroll interception, stable reel identity, back behavior from DMs, or
  a single-reel lock; no gesture was attempted in the DM viewer.
- If positive signals are missing, allow. Keep future IDs/label strings in
  `ReelsRules.kt`, overlay handling in `OverlayManager`, and state transitions in
  one place. Recheck evidence after Instagram updates.

## Missing capture and exact next step

All 11 required states were captured. Only the optional
`home_end_of_feed_with_followed_posts` viewport is missing. The earlier inspected
Home positions contained ordinary posts, suggestions, and ads without a marker;
the user subsequently exposed the marker, but it directly followed the stories
tray with no ordinary post above it. To capture the mixed viewport, Instagram must
load followed posts immediately before this marker. Manually expose that boundary
with the preceding post still visible and report ready; capture the optional
variant without refreshing. Do not substitute a suggested post or stories for the
requested followed-post context. A photo-only Explore viewer and single-reel-lock
behavior remain untested beyond the requested captures.

## Stage checks

- `gradlew.bat assembleDebug`: passed (existing outputs up to date).
- `gradlew.bat test`: passed (existing results up to date; 6 tests, 0 failures/errors).
- All 11 required-state XML files parse; matching PNGs are 1440 x 3168. The extra
  `home_followed_post_next_header` pair also passes these checks. Only the optional
  end-marker mixed viewport has no capture pair.
- No app code, capture script, permissions, or device settings changed. No install,
  likes, follows, comments, messages, posts, or DM navigation performed by the agent.
- Commit scope: only `docs/rules-map.md`; dumps and screenshots remain local.

## Stage 3: Reels blocking and the DM single-reel lock

Implemented on 2026-11-06. All matching IDs remain in `ReelsRules.kt`;
`GuardStateMachine` owns the DM lock and navigation latches. No overlay is needed.

- A selected `clips_tab` (including selection on its `tab_icon` child), or the
  captured Reels header together with the player, requests a click on `feed_tab`.
  If Home cannot be clicked, the service falls back to BACK.
- A full-screen player requires all three visible signals: `root_clips_layout`,
  `clips_viewer_view_pager`, and `clips_single_media_component`. The Explore
  contrast additionally uses `comment_composer_text_view` plus
  `clips_viewer_action_bar_title`, without matching arbitrary title text.
- DM origin is checked first, in the current clips subtree: visible
  `sender_username_or_fullname`, `reply_bar_container_and_title`, and either
  `reel_viewer_message_composer` or `reply_bar_edittext`. Following status is not
  consulted. Partial DM chrome fails open and reports missing IDs in debug mode.
- Reject zero-area, inverted and wholly off-screen bounds before matching.
  A hidden clips root cannot keep a lock alive through stale visible children.
- The service processes structural snapshots at 125 ms intervals. While a lock
  or navigation latch is active, it also checks the active root at that interval,
  so leaving Instagram clears state despite the Instagram-only event filter.
  Unavailable children or failed reads abort that snapshot and allow content.

### Swipe evidence and chosen fingerprint

The user manually opened a sent reel, then swiped once; the agent did not open
the inbox or any thread. Redacted service trees were captured as
`dumps/dm_reel_before_swipe.service.txt` and
`dumps/dm_reel_after_swipe.service.txt`, with derived XML alongside them. Both
completed without unavailable children or failed nodes (101 before, 98 after).
`dumps/dm_reel_swipe_changes.txt` recorded changed paths, resource IDs and bounds
only. No Instagram text was captured by these service dumps.

- The pager emitted two identical TYPE_VIEW_SCROLLED events:
  `fromIndex=1`, `toIndex=1`, `scrollY=0`, `itemCount=7`.
- The `clips_single_media_component` path relative to the pager changed from
  `0/0/0/0/0` to `0/0/0/1/0`. These are hierarchy indices, not screen ordering.
- Pager bounds changed from `[0,160][1440,2752]` to
  `[0,160][1440,2736]`. The reply bar and message composer disappeared; the
  sender header remained. Sender presence alone would therefore permit browsing.

The lock retains the visible media item's relative hierarchy path and the last
available pager position (`fromIndex`, `toIndex`, `scrollY`). Visible area chooses
the displayed item when multiple items exist; tied areas are uncertain. Bounds
dimensions, sender text and captions are not identity. The initial unknown pager
position is seeded by the first valid event; later changes or a different item
path request BACK. Duplicate events cannot request another BACK while the outgoing
viewer remains. Reel identity is discarded immediately after that decision; the
outgoing-frame latch clears when the player closes. Returning to a thread,
foreground loss and service interruption reset the lock. No text hash was needed.

The captured paths and event fields are verified for this Instagram version;
they are not permanent media IDs. If an update reuses the path and withholds pager
positions, identity is uncertain and the rule allows content rather than guessing.

### Verification

- `scripts/capture.bat` was fixed and committed separately as `a647b62`: remove
  `/sdcard/ui.xml` before dumping, reject nonzero status or output containing
  `ERROR`, and never pull after failure. Mocked ERROR-with-success, nonzero dump,
  and successful dump paths passed. Build and unit tests passed for that commit.
- Stage 3: `gradlew.bat assembleDebug` and `gradlew.bat test` passed; 18 tests,
  zero failures/errors. The new tests load fake trees retaining only IDs, bounds,
  selection and hierarchy from the captured evidence. They cover Reels entry,
  non-DM players, the DM exception, missing signals, actual before/after changes,
  duplicate BACK suppression, pager changes with reused item paths, a second
  sent reel, foreground/thread reset and invalid bounds.
- Manifest checked before every install: no INTERNET permission. Debug APK
  installed successfully. Service package/event configuration remains unchanged.
- Phone: derived Reels tap `(432,2896)` from its current XML bounds
  `[288,2800][576,2992]`. Logcat reported `Home tab clicked`; the subsequent
  snapshot had Home selected and Reels unselected.
- Phone: the first sent reel remained allowed. With the final build, page 0
  (`fromIndex=0`, `toIndex=0`, `scrollY=0`) was observed before the user swiped;
  page 1 followed. Logcat recorded exactly one fingerprint-change decision,
  exactly one successful BACK, then the player-close/thread-return reset.
  A different sent reel manually opened afterward armed a fresh lock and stayed
  allowed. No agent DM navigation or social interaction was performed.
- Capture retention correction for Stage 4: **never delete `dumps/` or `shots/`**.
  The user's current instruction and the final instruction in AGENTS.md require
  retaining both directories until the user explicitly deletes them. The earlier
  Stage 3 deletion note was incorrect. Captures remain local and uncommitted.

## Stage 4: Home feed groups and whole-post covers

Implemented from `home_followed_post`, `home_followed_post_next_header`,
`home_sponsored_post`, `home_inline_reel`, and `home_scan_01` through `08`.
The twelve sanitized trees in `app/src/test/resources/stage4/` retain only IDs,
bounds, hierarchy, selection, and fixed-label tags. No Instagram text or
descriptions are retained in those fixtures.

- Require selected Home (`feed_tab` or its `tab_icon`) and a visible
  `android:id/list` plus `tab_bar`. Search results and clips viewers are excluded.
  Reject inverted, zero-area, and wholly off-screen bounds.
- Sort direct list rows by bounds. Split groups at `row_feed_profile_header` tops
  and the end-marker bounds, including a leading group for the previous post.
  A cover is the union of row bounds within that group's band, clipped below the
  visible `main_feed_action_bar` / `reels_tray_container` and above `tab_bar`.
  Media, CTA, actions, captions and feedback rows share their post's cover.
- Non-followed signals: `inline_follow_button` inside a visible Home post header;
  `secondary_label` tagged AD; a media description tagged SPONSORED; or the fixed
  SUGGESTED label. SUGGESTED_FOR_YOU is a separate tag and is insufficient alone.
  A Follow button outside a post header does not establish a blocked post.
- Inline-reel signals: `video_container` plus any of
  `feed_preview_keep_watching_button`, `feed_preview_keep_watching_backdrop`, or
  `feed_preview_watch_again_text`. Friend bubbles and ordinary video alone do not
  establish an inline reel.
- `GuardStateMachine` owns feed continuity, group tokens, and skip latches alongside
  the existing DM lock. Scroll deltas project the preceding group through a
  header-off-screen viewport. A unique complete row with matching structural IDs
  and height can also establish movement; the ad CTA in the supplied captures
  moves from y=2318 to y=256. Repeated or conflicting anchors are ambiguous.
  Runtime snapshots also retain the framework window ID and node hash (derived
  from accessibility source-node identity, not text) for visible media/action
  nodes. This maintains continuity when Instagram emits content changes without
  usable scroll events. A newly visible header always receives fresh classification;
  conflicting geometry and node evidence fails open. These runtime keys are not
  exposed by the XML dumps; the test adds explicit fake keys to the captured shapes.
  Missing positive classification or uncertain continuation fails open and emits
  only fixed missing-signal diagnostics in debug mode.
- `OverlayManager` owns opaque black `TYPE_ACCESSIBILITY_OVERLAY` windows on the
  main thread. Each displays “Not from someone you follow” or “Reel hidden”, consumes
  touches inside its own rectangle, and lets outside touches through. Views are
  pooled after removal. Home updates run at 100 ms; foreground checks clear windows
  after Instagram loses foreground. Interrupt, unbind, destroy and revoked consent
  also remove windows. Incomplete snapshots hide covers for that sample and retain
  DM identity and navigation cooldowns.
- “Auto-skip blocked items” is persisted locally and defaults off. When enabled,
  it requests `ACTION_SCROLL_FORWARD` on the Home feed once per tracked blocked
  group, with a 500 ms cooldown. The service rechecks Home immediately before
  scrolling. No social actions, arbitrary text identity, network code or additional
  permissions were introduced.

### Stage 4 checks

- `gradlew.bat assembleDebug test`: passed; 31 tests, zero failures/errors. Tests
  cover the followed post, full ad-row union, next-header separation, shuffled tree
  order, header-off-screen continuation, inline reels, stories and bar exclusion,
  header-only Follow scoping, changing subtitles, fixed Suggested/Sponsored tags,
  invalid bounds, uncertain continuation, skip latches, cooldown and state reset.
- Source and merged manifests checked before installation: no INTERNET permission.
  The Instagram-only accessibility package and three event types are unchanged.
- `scripts/capture-redacted.ps1` captures UI Automator XML through a pseudo-terminal
  into process memory, strips all text/descriptions, and saves structural fields
  and fixed-label tags only. It rejects non-Instagram and recognized DM screens.
  No raw XML file is created on the phone or host by this script. Screenshots and
  redacted captures stay in the existing gitignored `shots/` and `dumps/` folders.
- Phone screenshots: `stage4_inline_reel` preserves stories and bars while hiding
  the reel; `stage4_continuity_02` retains its header-off-screen tail above an
  allowed post; `stage4_final_scroll_02` / `03` show an entire suggested post
  covered below an uncovered post. `stage4_ad_stationary` pairs a cover at
  `[0,679][1440,2800]` with a redacted AD header and CTA at y=2327..2503.
  `stage4_ad_tail_uninterrupted` shows the same ad covered at
  `[0,160][1440,1734]` after its header disappears, stopping at the next allowed
  post. The user restored auto-skip to off after successful scroll-action checks.
  `stage4_left_home` confirms the Home windows disappear on the Search tab.
  No DM navigation, likes, follows, comments, messages or posts were performed.
- Verification caveat: UI Automator capture temporarily interrupts accessibility
  tracking on this phone, resetting the remembered post. A fresh header-off-screen
  ad without a Sponsored description then correctly fails open. For continuity
  checks, expose the header first, scroll, allow the cover update to settle, and
  take a screenshot **without an intervening UI Automator dump**. Frames captured
  during a fling can precede the next 100 ms cover update. These transient frames
  must not be presented as settled cover verification.

## Stage 5: End-of-followed-content wall

- Require selected Home, a visible feed list and tab bar, and usable bounds.
  The visible `end_of_feed_demarcator_container` must be a **direct list child**.
  Its ID establishes the wall immediately; subtitle matching is not required.
  The sanitized `stage5/home_end_of_feed.xml` fixture retains structure and fixed
  label tags only. Stage 4's sanitized `home_scan_*` fixtures supply group evidence.
- `OverlayManager` owns one independent opaque accessibility overlay for the wall,
  displaying "You're all caught up" and consuming touches in its rectangle.
  It starts at the marker's top and ends at the tab bar's top: the captured
  rectangle is `[0,902][1440,2800]`. Stories above it remain outside the window.
  Older posts is covered. Per-post windows are clipped above the wall.
- Without a marker, N consecutive positively blocked groups also establish the
  wall at the first group's top. N defaults to 3, is adjustable to any positive
  integer, and is saved locally. Group tokens prevent repeated frames from
  increasing the count. Proven scroll movement carries counts and the initial
  boundary across frames; unknown groups break runs, and uncertain movement
  discards old wall geometry before recomputing from current positive signals.
- The separate "Stop scrolling at end of followed content" switch defaults on.
  Turning it off removes the wall and its run history. Changing N recomputes it.
  Auto-skip is suppressed while a wall is visible so the feed can remain at rest.
- An ordinary post with a complete visible header plus media/actions above the
  wall permits `ACTION_SCROLL_BACKWARD` when the wall rises above 60% of the
  visible list height. The state machine limits requests to one per 400 ms.
  Both snapshot and dispatch must expose the list's backward action. Stories,
  unidentified tails, and partial headers do not qualify as an ordinary post.
  The supplied captures expose no positive following-status flag: this uses the
  same ordinary-post reference as Stage 4, without treating an unknown tail as
  proof of followed content. No qualifying post above means no backward request.
- Leaving Home, losing foreground, service interruption, or revoked consent clears
  wall state. A return-to-top (explicit list scroll event, or a transition to a
  non-backward-scrollable list with stories) followed by content change resets
  wall and group history before recomputing. Periodic unchanged snapshots do not
  constitute refreshes. Verification must not refresh the real feed.
- Debug diagnostics report consecutive count, `marker-id` / `blocked-run` /
  `none` / `disabled`, missing structural signals, and whether backward scrolling
  was requested. Dispatch logs separately report whether the action actually
  fired. No UI text is logged or retained.

### Stage 5 checks

- `gradlew.bat assembleDebug test` passes: 44 tests, zero failures/errors,
  including marker bounds, Older posts coverage,
  stories exclusion, reversed child order, 2 vs 3 groups, intervening ordinary
  posts, repeated-frame deduplication, run continuity through captured scans,
  projected marker geometry, backward availability, cooldown, all reset paths,
  setting persistence, changed N, disabled wall, and invalid/missing signals.
- Source and merged manifests have no INTERNET permission; the service package,
  event filter, and content-retrieval configuration are unchanged.
- Installed and verified on the connected phone. The initial redacted capture
  `stage5_before_install` had no marker; after the user could not expose it,
  four Page Up navigation events reached the already loaded boundary without
  refreshing. `stage5_marker_verified.xml` confirms direct-child marker
  `[0,902][1440,1316]`, stories `[0,384][1440,902]`, and tab bar top y=2800.
  `shots/stage5_touch_consumed.png` shows the settled wall from y=902 to y=2800,
  covering Older posts and all subsequent feed content. An upward swipe entirely
  inside that rectangle logged `Overlay touch consumed wall=true`; the settled
  screenshot still shows stories above the wall. A horizontal swipe derived from
  the stories bounds moved the tray while preserving the wall, recorded in
  `shots/stage5_stories_usable.png`. Diagnostics report `followedAbove=false`,
  `canScrollBackward=false`, and `SCROLL_BACKWARD=false` on this boundary.
- Verification limitation: incomplete snapshots intermittently fail open on this
  phone, temporarily clearing overlays as required by the uncertainty policy.
  Early swipe-check screenshots lost the stories region and are not evidence of
  reliable interception. The later settled check above explicitly confirms the
  overlay received the gesture. Debug errors now identify the processing phase
  and exception class only, without exception messages or UI data. The mixed
  followed-post/marker viewport remains covered by fake-tree tests rather than
  a real capture. No feed refresh, DM navigation, or social actions were performed.
  Local dumps and shots are preserved and remain gitignored.

## Stage 6: Explore capture evidence

Captured on 2026-11-06 at 1440 x 3168. All navigation targets came from XML
bounds. No DM navigation or social actions occurred. Captures remain local.

| State | Capture source | Observations |
| --- | --- | --- |
| `explore_photo_viewer` | `scripts\capture.bat`, UI Automator, redacted in memory | Opened a photo carousel from grid `image_button` `[0,2485][476,2800]`, tap `(238,2642)`. `root_clips_layout`, `clips_viewer_view_pager` `[0,160][1440,2752]`, `clips_carousel_image_media_content` `[0,384][1440,2304]`, and `clips_viewer_action_bar_title` `[256,160][1216,384]`; no `tab_bar` and **no `clips_single_media_component`**. Screenshot confirms photo carousel in Explore's clips shell. Require title plus shell/pager and no visible tab bar; do not require the single-media ID for this photo case. |
| `profile_from_search` | `scripts\capture.bat`, UI Automator, redacted in memory | Account opened through `row_search_user_username` bounds `[320,1280][576,1351]`. Profile has `profile_action_bar` `[0,160][1440,384]`, `profile_header_container` `[0,384][1440,1980]`, `profile_tab_layout` `[0,1787][1440,1979]`, and grid `image_button` nodes. No clips shell/title, Explore action bar, or bottom tab bar. This is an allowed profile, not an Explore viewer. |
| `profile_post_viewer` | `scripts\capture.bat`, UI Automator, redacted in memory | Opened first profile grid post `[0,1980][476,2615]`, tap `(238,2297)`. `android:id/list` `[0,384][1440,2992]`, `row_feed_profile_header` `[0,384][1440,592]`, and `action_bar` `[0,160][1440,384]` with `action_bar_button_back`. No selected Home tab, clips shell/title, Explore action bar, or bottom tab bar. Feed-like rows alone must not activate Home rules. |
| `search_result_reel_viewer` | `scripts\capture.bat`, UI Automator, redacted in memory | Reel available in results: `media_container` `[0,1908][717,2864]` with `preview_clip_play_count`; tap `(358,2386)`. Viewer has clips root, pager, single-media component, `clips_viewer_action_bar_title` `[256,160][1024,384]`, and no `tab_bar`. Existing Stage 3 guard immediately returns to results when UI Automator detaches. Capture was started before navigation while UI Automator was attached. `shots/search_result_reel_viewer_attached.png` shows the entering viewer transition; `shots/search_result_reel_viewer.png` shows returned results, **not** a settled viewer. Neither is claimed as settled viewer verification. |

Additional fresh states: `stage6_search_focused`, `stage6_search_typed`, and
`stage6_search_results` all succeeded through `scripts\capture.bat` / UI Automator.
Focused Search exposes a focused `android.widget.EditText` search field plus
`search_section_container` / `search_keyword_title`; typed Search exposes the
focused edit field plus `search_keyword_title`; submitted results expose the edit
field, `tabbed_pager`, and `search_tab_bar_layout` together. No query text is saved
or compared. Idle's search field is a Button, unfocused, even though the service
reports `editable=true`; **editability alone is insufficient**.

`stage6_explore_idle` and `stage6_explore_grid_photo_scan` failed UI Automator's idle
check through `scripts\capture.bat` and used `scripts\capture-service.ps1` instead.
The fallback saves redacted service logs and derived XML explicitly marked
`source="ReelsGuard-service-fallback"`; no focus flags are invented. Complete
trees have 421 and 443 nodes respectively, with zero unavailable children or
failed nodes. Idle has selected `search_tab`, `explore_action_bar`
`[0,160][1440,384]`, `pill_bar_rv` `[0,417][1440,593]`, and `tab_bar`
`[0,2800][1440,2992]`. The proposed cover is `[0,384][1440,2800]`, leaving
Search and Saved above it. Old Home/profile subtrees have inverted/off-screen
bounds and must be rejected before any matching.

Capture tooling now routes `capture.bat` through the existing in-memory redactor,
retaining structural class/focus fields. The on-demand service dump was shortened
and paced to prevent Android dropping its completion/tail logs, and now includes
focus/editability booleans. A diagnostic-only build was installed to obtain these
captures; no Explore blocking was implemented at capture time. Source and merged
manifests had no INTERNET permission. `gradlew.bat assembleDebug test` passed
(44 tests). This evidence commit includes only `docs/rules-map.md`; diagnostic
tooling changes remain for the implementation commit.

### Stage 6 implementation and verification

- `ReelsRules.explore` derives IDLE, SEARCH_ACTIVE, and VIEWER from live structural
  signals after rejecting non-positive-area and wholly off-screen nodes. Idle
  requires selected Search, the Explore action bar, a tab bar, and usable cover
  bounds. Active Search requires a focused/editable EditText, an EditText with
  keyword/section structure, or the results pager and results tab layout together.
  Each alternative uses multiple signals. Arbitrary text is never inspected for
  query presence. Home matching explicitly excludes Search/Explore/profile chrome,
  so Follow controls in results cannot activate Home post rules.
- `OverlayManager` owns one reserved opaque, touch-consuming Explore window at
  `[0,384][1440,2800]` on the captured screen. Search, Saved, and bottom navigation
  remain outside it. Active Search removes the window entirely; returning to idle
  recreates it. Foreground loss, interruption, consent removal and uncertain
  snapshots clear windows using the existing lifecycle handling.
- The existing state machine checks the DM origin/lock first. Clips root + pager
  + `clips_viewer_action_bar_title` with no visible `tab_bar` establishes a
  discovery viewer, including photo carousels without the single-media component.
  BACK is latched once through repeated outgoing frames. A profile reached during
  Search retains its allowed origin through its post viewers; returning to Search
  results clears that profile origin. Profiles and their feed-style post viewers
  do not match idle Explore or Home. Foreground loss resets navigation context.
- "Block Explore" defaults on, persists locally, and disables Explore covers and
  discovery-viewer BACK when off. Existing Reels-tab and DM-lock behavior remains.
  Diagnostics contain only state, fixed signals, missing structural requirements,
  and action outcomes. Debug dumps now emit short structural lines asynchronously
  in batches, avoiding log-rate loss without pausing event processing.
- Eight sanitized Stage 6 tree fixtures retain IDs, bounds, selection, class and
  focus/editability fields only. `gradlew.bat assembleDebug test` passed: **54
  tests, zero failures/errors**. They cover cover bounds, focused/typed/results
  Search, idle return, multi-signal requirements, child tab selection, invalid
  bounds, photo/grid/search viewer single-BACK latches, DM and partial-DM priority,
  profile posts, stale Home selection with Search Follow controls, disabled
  behavior, foreground reset, and setting persistence. DM priority uses the
  retained Stage 3 trees; no live DM navigation was performed.
- Installed after checking both source and merged manifests for absence of
  INTERNET permission. Accessibility package/event configuration is unchanged.
  `shots/stage6_installed_idle.png` and `shots/stage6_final_idle.png` show Search
  and Saved above the full opaque cover, with bottom navigation exposed.
  `stage6_idle_touch_consumed.png` plus logcat confirms a swipe inside the cover
  was consumed. `stage6_verified_search_focused.png`, `_typed.png`, and
  `_results.png` show the cover absent through query entry and submitted results.
  `dumps/stage6_verified_profile.diagnostics.txt` records SEARCH_ACTIVE with the
  results pager/tab pair and the allowed profile transition.
- `stage6_verified_profile.png`, `_profile_post.png`, and `_profile_scrolled.png`
  show the profile, opened grid post, and scrolled profile grid without covers.
  `dumps/stage6_verified_reel_back.log` records VIEWER, exactly **one**
  `BACK dispatched success=true`, and return to SEARCH_ACTIVE.
  `stage6_verified_idle_return.diagnostics.txt` then records IDLE; its paired
  screenshot shows the cover restored. The final installed build's asynchronous
  fallback completed with 401 nodes, zero unavailable children and zero failures.
  Captures used navigation only; no social actions or DM navigation occurred.
  `dumps/` and `shots/` are preserved, gitignored and uncommitted.

## Stage 7: Settings, README, and final audit

The Material 3 screen exposes Show toast, Debug mode, Auto-skip, the end wall,
N, and Block Explore, with real DataStore/service wiring. Status is read from
Android; consent remains persisted and its disclosure can be reviewed at any
time. Toast feedback is a two-second touch-through accessibility window owned by
`OverlayManager`, since system toasts were obscured by covers on the test phone.
Debug mode gates Logcat in both variants; on-demand dumps remain debug-only.
The fallback capture helper now keeps debug logs in memory and saves only derived
structural XML; historical exported logs remain preserved.

`gradlew.bat assembleDebug assembleRelease test` passes with 55 unit tests and no
failures. The release APK is non-debuggable and deliberately signed with the
existing local debug key for personal sideloading. It was signature-verified,
installed in place, and checked on the phone. Both merged manifests and the
compiled accessibility XML passed the permissions/package/event audit. Cleanup
polling now also tracks remaining windows after a state reset.

See [the final audit](final-audit.md) for every required check, phone evidence,
restored settings, and exact verification limits. No matching rules changed in
this stage. [README](../README.md) now documents builds, sideloading, restricted
settings, all controls, rule updates, privacy and known limitations. No captures
were deleted or committed.
