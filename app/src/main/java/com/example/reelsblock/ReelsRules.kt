package com.example.reelsblock

import android.view.accessibility.AccessibilityEvent

/** Instagram IDs and matching, derived from the local Stage 2 through 6 captures. */
object ReelsRules {
    const val INSTAGRAM_PACKAGE = "com.instagram.android"
    const val CLIPS_PAGER = "$INSTAGRAM_PACKAGE:id/clips_viewer_view_pager"
    const val HOME_TAB = "$INSTAGRAM_PACKAGE:id/feed_tab"
    const val REELS_TAB = "$INSTAGRAM_PACKAGE:id/clips_tab"
    const val TAB_ICON = "$INSTAGRAM_PACKAGE:id/tab_icon"
    const val CLIPS_ROOT = "$INSTAGRAM_PACKAGE:id/root_clips_layout"
    const val CLIPS_ITEM = "$INSTAGRAM_PACKAGE:id/clips_single_media_component"
    const val REELS_HEADER = "$INSTAGRAM_PACKAGE:id/action_bar_tab_layout"
    const val DM_SENDER = "$INSTAGRAM_PACKAGE:id/sender_username_or_fullname"
    const val DM_REPLY = "$INSTAGRAM_PACKAGE:id/reply_bar_container_and_title"
    const val DM_COMPOSER = "$INSTAGRAM_PACKAGE:id/reel_viewer_message_composer"
    const val DM_EDIT = "$INSTAGRAM_PACKAGE:id/reply_bar_edittext"
    const val EXPLORE_COMPOSER = "$INSTAGRAM_PACKAGE:id/comment_composer_text_view"
    const val VIEWER_TITLE = "$INSTAGRAM_PACKAGE:id/clips_viewer_action_bar_title"
    const val FEED_LIST = "android:id/list"
    const val FEED_HEADER = "$INSTAGRAM_PACKAGE:id/row_feed_profile_header"
    const val FOLLOW_BUTTON = "$INSTAGRAM_PACKAGE:id/inline_follow_button"
    const val SECONDARY_LABEL = "$INSTAGRAM_PACKAGE:id/secondary_label"
    const val VIDEO = "$INSTAGRAM_PACKAGE:id/video_container"
    const val KEEP_WATCHING = "$INSTAGRAM_PACKAGE:id/feed_preview_keep_watching_button"
    const val REEL_BACKDROP = "$INSTAGRAM_PACKAGE:id/feed_preview_keep_watching_backdrop"
    const val WATCH_AGAIN = "$INSTAGRAM_PACKAGE:id/feed_preview_watch_again_text"
    const val FEED_END = "$INSTAGRAM_PACKAGE:id/end_of_feed_demarcator_container"
    const val STORIES = "$INSTAGRAM_PACKAGE:id/reels_tray_container"
    const val TOP_BAR = "$INSTAGRAM_PACKAGE:id/main_feed_action_bar"
    const val TAB_BAR = "$INSTAGRAM_PACKAGE:id/tab_bar"
    const val SEARCH_RESULTS = "$INSTAGRAM_PACKAGE:id/search_tab_bar_layout"
    const val SEARCH_TAB = "$INSTAGRAM_PACKAGE:id/search_tab"
    const val EXPLORE_BAR = "$INSTAGRAM_PACKAGE:id/explore_action_bar"
    const val SEARCH_EDIT = "$INSTAGRAM_PACKAGE:id/action_bar_search_edit_text"
    const val SEARCH_KEYWORD = "$INSTAGRAM_PACKAGE:id/search_keyword_title"
    const val SEARCH_SECTION = "$INSTAGRAM_PACKAGE:id/search_section_container"
    const val SEARCH_PAGER = "$INSTAGRAM_PACKAGE:id/tabbed_pager"
    const val PROFILE_BAR = "$INSTAGRAM_PACKAGE:id/profile_action_bar"
    const val PROFILE_TABS = "$INSTAGRAM_PACKAGE:id/profile_tab_layout"
    const val PROFILE_HEADER = "$INSTAGRAM_PACKAGE:id/profile_header_container"
    const val EDIT_CLASS = "android.widget.EditText"
    const val MEDIA_GROUP = "$INSTAGRAM_PACKAGE:id/media_group"
    const val PHOTO = "$INSTAGRAM_PACKAGE:id/row_feed_photo_imageview"
    const val CAROUSEL_MEDIA = "$INSTAGRAM_PACKAGE:id/carousel_video_media_group"
    const val CTA = "$INSTAGRAM_PACKAGE:id/cta_container"
    const val FEED_BUTTONS = "$INSTAGRAM_PACKAGE:id/row_feed_view_group_buttons"

    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun clip(other: Bounds): Bounds? = if (intersects(other)) Bounds(
            maxOf(left, other.left), maxOf(top, other.top),
            minOf(right, other.right), minOf(bottom, other.bottom),
        ) else null
        fun union(other: Bounds) = Bounds(minOf(left, other.left), minOf(top, other.top),
            maxOf(right, other.right), maxOf(bottom, other.bottom))
        fun intersects(other: Bounds): Boolean = right > left && bottom > top &&
            other.right > other.left && other.bottom > other.top &&
            left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }

    // No text, descriptions, framework nodes, or events escape the snapshot reader.
    data class Node(
        val id: String?,
        val bounds: Bounds,
        val selected: Boolean = false,
        val children: List<Node> = emptyList(),
        val labels: Set<Label> = emptySet(),
        val descriptionLabels: Set<Label> = emptySet(),
        // Framework window/node identity only; never text or an account/media hash.
        val sourceKey: Pair<Int, Int>? = null,
        val canScrollBackward: Boolean? = null,
        val focused: Boolean = false,
        val editable: Boolean = false,
        val className: String? = null,
    ) {
        fun descendants(): Sequence<Node> = sequence {
            yield(this@Node)
            children.forEach { yieldAll(it.descendants()) }
        }
    }

    enum class Screen { OTHER, UNCERTAIN, REELS_TAB, PLAYER, EXPLORE_PLAYER, DM_PLAYER }
    data class Match(val screen: Screen, val missing: String = "")
    data class Fingerprint(val itemPath: List<Int>)
    data class PagerPosition(val fromIndex: Int, val toIndex: Int, val scrollY: Int)

    enum class ExploreState { IDLE, SEARCH_ACTIVE, VIEWER }
    data class ExploreMatch(
        val state: ExploreState? = null, val cover: Bounds? = null,
        val signals: String = "", val missing: String = "",
    ) {
        val diagnostic: String get() = "Explore state=${state?.name ?: "UNCERTAIN"} signals=$signals missing=$missing"
    }

    private fun selectedTab(nodes: List<Node>, id: String, viewport: Bounds): Boolean = nodes.any { tab ->
        tab.id == id && (tab.selected || tab.children.any {
            it.id == TAB_ICON && it.selected && it.bounds.intersects(viewport)
        })
    }

    fun isProfile(root: Node, viewport: Bounds): Boolean {
        val ids = root.descendants().filter { it.bounds.intersects(viewport) }.mapNotNull { it.id }.toSet()
        return PROFILE_BAR in ids && (PROFILE_TABS in ids || PROFILE_HEADER in ids)
    }

    fun explore(root: Node, viewport: Bounds): ExploreMatch {
        val visible = root.descendants().filter { it.bounds.intersects(viewport) }.toList()
        val ids = visible.mapNotNull { it.id }.toSet()
        val viewer = visibleViewer(root, viewport).mapNotNull { it.id }.toSet()
        if (CLIPS_PAGER in viewer && VIEWER_TITLE in viewer && TAB_BAR !in ids) {
            return ExploreMatch(ExploreState.VIEWER, signals = "clips-root,pager,viewer-title,no-tab-bar")
        }
        val edit = visible.singleOrNull { it.id == SEARCH_EDIT }
        val editField = edit?.className == EDIT_CLASS
        val focusedEdit = editField && edit?.focused == true && edit.editable
        val suggestions = SEARCH_KEYWORD in ids || SEARCH_SECTION in ids
        val results = SEARCH_PAGER in ids && SEARCH_RESULTS in ids
        // Every alternative requires at least two live structural/state signals.
        // Idle exposes editable=true on a Button; never inspect arbitrary query text.
        if (focusedEdit || (editField && suggestions) || results) {
            return ExploreMatch(ExploreState.SEARCH_ACTIVE,
                signals = "editField=$editField,focusedEditable=$focusedEdit,suggestions=$suggestions,pagerAndTabs=$results")
        }
        val selected = selectedTab(visible, SEARCH_TAB, viewport)
        val bar = visible.singleOrNull { it.id == EXPLORE_BAR }
        val tab = visible.singleOrNull { it.id == TAB_BAR }
        if (selected && bar != null && tab != null) {
            val rect = Bounds(viewport.left, bar.bounds.bottom, viewport.right, tab.bounds.top).clip(viewport)
            if (rect != null) return ExploreMatch(ExploreState.IDLE, rect,
                "search-tab-selected,explore-action-bar,tab-bar")
        }
        return ExploreMatch(signals = "searchTab=$selected,exploreBar=${bar != null}",
            missing = "focused editable edit field or edit field with suggestions or results pager+tabs; " +
                "idle needs selected Search, visible Explore action bar, tab bar and usable cover bounds; " +
                "viewer needs clips root+pager+title without tab bar")
    }

    enum class CoverKind { NON_FOLLOWED, INLINE_REEL }
    data class FeedGroup(
        val rect: Bounds, val headerTop: Int?, val boundary: Int,
        val kind: CoverKind?, val rows: List<Node>, val previousIndex: Int? = null,
        val originTop: Int = headerTop ?: rect.top,
    )
    data class FeedFrame(
        val area: Bounds, val groups: List<FeedGroup>, val missing: String = "",
        val markerTop: Int? = null, val movement: Int? = null,
        val canScrollBackward: Boolean? = null, val viewportTop: Int = area.top,
        val atTop: Boolean = false,
    )

    // An ordinary captured post is the conservative allowed-post reference. An
    // unidentified leading tail or a stories row cannot authorize navigation.
    fun ordinaryPost(group: FeedGroup): Boolean = group.kind == null && group.headerTop != null &&
        group.rows.any { row -> row.descendants().any {
            it.id == FEED_HEADER && it.bounds.clip(group.rect) == it.bounds
        } } &&
        group.rows.any { row -> row.descendants().any {
            it.bounds.intersects(group.rect) && it.id in listOf(MEDIA_GROUP, PHOTO, CAROUSEL_MEDIA, VIDEO, FEED_BUTTONS)
        } }

    fun wallRect(frame: FeedFrame, top: Int): Bounds? = Bounds(
        frame.area.left, maxOf(frame.area.top, top), frame.area.right, frame.area.bottom,
    ).takeIf { it.intersects(it) }

    fun wallNeedsBackward(frame: FeedFrame, top: Int, followedAbove: Boolean): Boolean =
        followedAbove && frame.canScrollBackward == true &&
            top < frame.viewportTop + (frame.area.bottom - frame.viewportTop) * 0.6f

    fun isHome(root: Node, viewport: Bounds): Boolean {
        val visible = root.descendants().filter { it.bounds.intersects(viewport) }.toList()
        return selectedTab(visible, HOME_TAB, viewport) &&
            !selectedTab(visible, SEARCH_TAB, viewport) &&
            visible.none { it.id in listOf(SEARCH_RESULTS, SEARCH_PAGER, SEARCH_KEYWORD, SEARCH_SECTION,
                EXPLORE_BAR, PROFILE_BAR, CLIPS_ROOT) }
    }

    /** Rows are direct list children; header position, never traversal order, splits posts. */
    fun feedGroups(
        root: Node, viewport: Bounds, previous: FeedFrame? = null, scrollDelta: Int? = null,
    ): FeedFrame? {
        if (!isHome(root, viewport)) return null
        val visible = root.descendants().filter { it.bounds.intersects(viewport) }.toList()
        val feed = visible.singleOrNull { it.id == FEED_LIST } ?: return null
        val tab = visible.singleOrNull { it.id == TAB_BAR } ?: return null
        val clipped = feed.bounds.clip(viewport) ?: return null
        val top = visible.filter { it.id == TOP_BAR || it.id == STORIES }
            .maxOfOrNull { it.bounds.bottom } ?: clipped.top
        val area = Bounds(clipped.left, maxOf(clipped.top, top), clipped.right,
            minOf(clipped.bottom, tab.bounds.top))
        if (!area.intersects(area)) return null
        val rows = feed.children.filter { it.bounds.intersects(area) }.sortedBy { it.bounds.top }
        val nodes = rows.flatMap { row -> row.descendants().filter { it.bounds.intersects(area) }.toList() }
        val headers = nodes.filter { it.id == FEED_HEADER }.distinctBy { it.bounds.top }
            .sortedBy { it.bounds.top }
        val markers = nodes.filter { it.id == FEED_END }.mapNotNull { it.bounds.clip(area) }
        val cuts = (listOf(area.top, area.bottom) + headers.map { maxOf(area.top, it.bounds.top) } +
            markers.flatMap { listOf(it.top, it.bottom) }).distinct().sorted()

        // Full, unclipped rows such as the captured CTA can establish scroll movement
        // without retaining usernames/captions. Repeated signatures are ambiguous.
        fun signature(row: Node) = row.descendants().mapNotNull { it.id }.sorted().toList()
        val oldRows = previous?.groups?.flatMap { it.rows }?.distinct().orEmpty()
        val shifts = mutableListOf<Int>()
        if (previous != null) for (row in rows) {
            if (row.bounds.top <= area.top || row.bounds.bottom >= area.bottom) continue
            if (row.descendants().any { it.id == FEED_HEADER }) continue
            val key = signature(row)
            if (key.isEmpty() || rows.count { signature(it) == key } != 1) continue
            val old = oldRows.singleOrNull { signature(it) == key &&
                it.bounds.top > previous.area.top && it.bounds.bottom < previous.area.bottom &&
                it.bounds.bottom - it.bounds.top == row.bounds.bottom - row.bounds.top }
            if (old != null) shifts.add(old.bounds.top - row.bounds.top)
        }
        val unchanged = oldRows.map { it.bounds to signature(it) }.toSet() ==
            rows.map { it.bounds to signature(it) }.toSet()
        val delta = scrollDelta ?: shifts.distinct().singleOrNull() ?: if (unchanged) 0 else null
        val groups = cuts.zipWithNext().mapNotNull { (start, end) ->
            if (markers.any { start >= it.top && start < it.bottom }) return@mapNotNull null
            val band = Bounds(area.left, start, area.right, end)
            val groupRows = rows.filter { it.bounds.intersects(band) }
            val rect = groupRows.mapNotNull { it.bounds.clip(band) }.reduceOrNull(Bounds::union)
                ?: return@mapNotNull null
            val header = headers.singleOrNull { maxOf(area.top, it.bounds.top) == start }
            val signals = groupRows.flatMap { it.descendants().filter { n ->
                n.bounds.intersects(band)
            }.toList() }
            val headerNodes = header?.descendants()?.filter { it.bounds.intersects(band) }?.toList().orEmpty()
            val reel = signals.any { it.id == VIDEO } && signals.any {
                it.id == KEEP_WATCHING || it.id == REEL_BACKDROP || it.id == WATCH_AGAIN
            }
            val nonFollowed = headerNodes.any { it.id == FOLLOW_BUTTON } ||
                signals.any { it.id == SECONDARY_LABEL && Label.AD in it.labels } ||
                signals.any { it.id in listOf(MEDIA_GROUP, PHOTO, CAROUSEL_MEDIA, VIDEO) &&
                    Label.SPONSORED in it.descriptionLabels } ||
                // The changing Suggested-for-you subtitle is insufficient by itself.
                signals.any { Label.SUGGESTED in it.labels }
            val geometryPrior = if (previous != null && delta != null) previous.groups.withIndex().singleOrNull { (_, old) ->
                val projectedTop = old.originTop - delta
                val projectedBottom = old.boundary - delta
                if (header != null) old.headerTop != null && kotlin.math.abs(projectedTop - header.bounds.top) <= 2
                else start == area.top && (projectedTop < area.top || (unchanged && projectedTop == area.top)) &&
                    projectedBottom > area.top
            }?.index else null
            fun keys(groupRows: List<Node>, rect: Bounds) = groupRows.asSequence()
                .flatMap { it.descendants() }.filter {
                    it.bounds.intersects(rect) && it.id in listOf(VIDEO, MEDIA_GROUP, PHOTO, CAROUSEL_MEDIA, CTA, FEED_BUTTONS)
                }.mapNotNull { n -> n.sourceKey?.let { n.id to it } }.toSet()
            val currentKeys = keys(groupRows, band)
            val identityPrior = previous?.groups?.withIndex()?.singleOrNull { (_, old) ->
                currentKeys.intersect(keys(old.rows, old.rect)).isNotEmpty()
            }?.index
            // Instagram sometimes emits only content changes, without scroll deltas.
            // A still-visible media/action node can carry its post through those frames.
            // Conflicting movement and node evidence is uncertain.
            val prior = when {
                geometryPrior != null && identityPrior != null && geometryPrior != identityPrior -> null
                else -> identityPrior ?: geometryPrior
            }
            val inherited = if (header == null && prior != null) previous?.groups?.get(prior)?.kind else null
            FeedGroup(rect, header?.bounds?.top, end,
                if (reel) CoverKind.INLINE_REEL else if (nonFollowed) CoverKind.NON_FOLLOWED else inherited,
                groupRows, prior, header?.bounds?.top ?: prior?.let {
                    (previous!!.groups[it].originTop - (delta ?: 0)).let { projected ->
                        if (header == null) minOf(projected, area.top - 1) else projected
                    }
                } ?: start)
        }
        val markerTop = rows.filter { it.id == FEED_END }.singleOrNull()?.bounds?.clip(area)?.top
        return FeedFrame(area, groups, if (groups.any { it.kind == null })
            "Home group: missing header Follow, secondary Ad, sponsored media, or video + inline-reel controls; allowing content"
        else "", markerTop, delta, feed.canScrollBackward, clipped.top,
            feed.canScrollBackward == false && visible.any { it.id == STORIES })
    }

    fun pagerPosition(
        sourceId: String?, bounds: Bounds, viewport: Bounds, eventType: Int,
        fromIndex: Int, toIndex: Int, scrollY: Int,
    ): PagerPosition? = if (sourceId == CLIPS_PAGER && bounds.intersects(viewport) &&
        eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && fromIndex >= 0 && toIndex >= 0
    ) PagerPosition(fromIndex, toIndex, scrollY) else null

    private fun visibleViewer(root: Node, viewport: Bounds): List<Node> = root.descendants()
        .firstOrNull { it.id == CLIPS_ROOT && it.bounds.intersects(viewport) }
        ?.descendants()?.filter { it.bounds.intersects(viewport) }?.toList().orEmpty()

    fun hasPlayer(root: Node, viewport: Bounds): Boolean = visibleViewer(root, viewport).any {
        it.id == CLIPS_PAGER
    }

    fun fingerprint(root: Node, viewport: Bounds): Fingerprint? {
        val pager = visibleViewer(root, viewport).firstOrNull {
            it.id == CLIPS_PAGER
        } ?: return null
        val items = mutableListOf<Pair<List<Int>, Long>>()
        fun visit(node: Node, path: List<Int>) {
            if (node.id == CLIPS_ITEM && node.bounds.intersects(viewport)) {
                val b = node.bounds
                val area = (minOf(b.right, viewport.right) - maxOf(b.left, viewport.left)).toLong() *
                    (minOf(b.bottom, viewport.bottom) - maxOf(b.top, viewport.top))
                items.add(path to area)
            }
            node.children.forEachIndexed { index, child -> visit(child, path + index) }
        }
        visit(pager, emptyList())
        // Choose the displayed item by visible area. The path is structural identity,
        // never a claim that traversal order describes visual order.
        val largest = items.maxOfOrNull { it.second } ?: return null
        return items.singleOrNull { it.second == largest }?.let { Fingerprint(it.first) }
    }

    fun classify(root: Node, viewport: Bounds): Match {
        val visible = root.descendants().filter { it.bounds.intersects(viewport) }.toList()
        val viewer = visibleViewer(root, viewport)
        val ids = viewer.mapNotNull { it.id }.toSet()
        val player = listOf(CLIPS_ROOT, CLIPS_PAGER, CLIPS_ITEM)
        val dm = listOf(DM_SENDER, DM_REPLY)
        val hasComposer = DM_COMPOSER in ids || DM_EDIT in ids

        // The current snapshot establishes DM origin, including senders we do not follow.
        // Partial DM chrome must fail open, rather than fall through to the generic block.
        if ((dm + listOf(DM_COMPOSER, DM_EDIT)).any { it in ids }) {
            val missing = (player + dm).filter { it !in ids } +
                if (hasComposer) emptyList() else listOf(DM_COMPOSER, DM_EDIT)
            return if (missing.isEmpty()) Match(Screen.DM_PLAYER)
            else Match(Screen.UNCERTAIN, missing.joinToString())
        }
        if (explore(root, viewport).state == ExploreState.VIEWER) return Match(Screen.EXPLORE_PLAYER)
        if (visible.any { tab -> tab.id == REELS_TAB && (tab.selected ||
                tab.children.any { it.id == TAB_ICON && it.selected && it.bounds.intersects(viewport) })
            }) return Match(Screen.REELS_TAB)
        if (player.all { it in ids }) {
            return Match(when {
                REELS_HEADER in ids -> Screen.REELS_TAB
                else -> Screen.PLAYER
            })
        }
        return if (visible.any { it.id in player }) {
            Match(Screen.UNCERTAIN, player.filter { it !in ids }.joinToString())
        } else Match(Screen.OTHER)
    }

    enum class Label(val value: String, val allowSuffix: Boolean = false, vararg val aliases: String) {
        FOLLOW("Follow", true),
        SUGGESTED("Suggested"),
        SUGGESTED_FOR_YOU("Suggested for you"),
        AD("Ad"),
        SPONSORED("Sponsored"),
        SHOPNOW("Shop now"),
        CAUGHTUP("You're all caught up", false, "You\u2019re all caught up"),
        OLDERPOSTS("View older posts", false, "Older posts"),
        REELBY("Reel by", true),
        PHOTOBY("Photo by", true),
        LIKEDREEL("Liked reel", true),
        SOUND("Sound", true, "Turn sound on", "Turn sound off"),
        SEARCH("Search", false, "Search and explore"),
    }

    fun labelMatches(label: Label, text: CharSequence?, description: CharSequence?): Boolean =
        (listOf(label.value) + label.aliases).any { fixed ->
            fun matches(value: CharSequence?): Boolean =
                fixed.contentEquals(value, ignoreCase = true) ||
                    (label.allowSuffix && value?.startsWith("$fixed ", ignoreCase = true) == true)
            matches(text) || matches(description)
        }

    fun descriptionLabels(description: CharSequence?): Set<Label> = Label.entries.filter {
        labelMatches(it, null, description) || (it == Label.SPONSORED && description != null &&
            Regex("(?i)\\bSponsored\\b").containsMatchIn(description))
    }.toSet()

    fun debugLabelTags(text: CharSequence?, description: CharSequence?): String =
        Label.entries.joinToString(" ") { "lbl=${it.name}=${labelMatches(it, text, description)}" }

    // Only fixed labels leave memory; descriptions can contain private captions or names.
    fun debugDescription(description: CharSequence?): String = when {
        description.isNullOrEmpty() -> "<none>"
        else -> Label.entries.firstOrNull { labelMatches(it, null, description) }?.name
            ?: "<redacted>"
    }

    fun acceptsEvent(packageName: CharSequence?, eventType: Int): Boolean =
        packageName != null && INSTAGRAM_PACKAGE.contentEquals(packageName) && when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> true
            else -> false
        }
}
