package com.example.reelsblock

/** One owner for the DM lock and navigation latches; retains structural values only. */
class GuardStateMachine {
    enum class Action { NONE, HOME_OR_BACK, BACK, SCROLL_FEED, SCROLL_BACKWARD }
    data class Cover(val groupId: Long, val rect: ReelsRules.Bounds, val kind: ReelsRules.CoverKind)
    data class Decision(
        val action: Action = Action.NONE, val diagnostic: String = "", val covers: List<Cover> = emptyList(),
        val wall: ReelsRules.Bounds? = null,
        val exploreCover: ReelsRules.Bounds? = null,
    )
    private enum class State { IDLE, DM_LOCKED, EXIT_SENT, BLOCK_SENT }
    private var state = State.IDLE
    private var fingerprint: ReelsRules.Fingerprint? = null
    private var page: ReelsRules.PagerPosition? = null
    private var feed: ReelsRules.FeedFrame? = null
    private var groupIds = emptyList<Long>()
    private val skipped = mutableSetOf<Long>()
    private var nextGroupId = 0L
    private var lastSkip: Long? = null
    private data class Run(val count: Int, val top: Int, val followedAbove: Boolean)
    private data class Wall(val top: Int, val signal: String, val followedAbove: Boolean, val count: Int)
    private var runs = emptyMap<Long, Run>()
    private var wall: Wall? = null
    private var lastBackward: Long? = null
    private var refreshPending = false
    private var wallLimit = 3
    private var searchSession = false
    private var profileFromSearch = false
    private var exploreActive = false
    val needsForegroundCheck: Boolean get() = state != State.IDLE || feed != null || exploreActive

    fun reset() {
        state = State.IDLE
        fingerprint = null
        page = null
        feed = null
        groupIds = emptyList()
        skipped.clear()
        searchSession = false
        profileFromSearch = false
        exploreActive = false
        resetWall()
    }

    private fun resetWall() {
        runs = emptyMap()
        wall = null
        refreshPending = false
    }

    fun step(
        root: ReelsRules.Node?, viewport: ReelsRules.Bounds,
        positions: List<ReelsRules.PagerPosition> = emptyList(),
        scrollDelta: Int? = null, autoSkip: Boolean = false, now: Long = 0L,
        stopAtEnd: Boolean = true, blockedGroupLimit: Int = 3, feedContentChanged: Boolean = false,
        feedScrolledToTop: Boolean = false,
        blockExplore: Boolean = true,
    ): Decision {
        if (root == null) {
            reset()
            return Decision(diagnostic = "Lock reset: Instagram left foreground or root unavailable")
        }
        val match = ReelsRules.classify(root, viewport)
        if (!ReelsRules.isHome(root, viewport)) {
            feed = null
            groupIds = emptyList()
            skipped.clear()
            resetWall()
        }
        val hasPlayer = ReelsRules.hasPlayer(root, viewport)
        if (state == State.EXIT_SENT) {
            // Clear reel identity immediately after deciding BACK, but keep a latch
            // through outgoing viewer frames so duplicate events cannot send it twice.
            if (!hasPlayer) reset()
            return Decision()
        }
        if (state == State.DM_LOCKED) {
            if (!hasPlayer) {
                reset()
            } else {
                val current = ReelsRules.fingerprint(root, viewport)
                var pageChanged = false
                for (position in positions) {
                    if (page != null && page != position) pageChanged = true
                    page = position
                }
                if ((current != null && current != fingerprint) || pageChanged) {
                    state = State.EXIT_SENT
                    fingerprint = null
                    page = null
                    return Decision(Action.BACK, "DM lock: reel fingerprint changed; BACK once")
                }
                return Decision(diagnostic = if (current == null) {
                    "Missing signals: unambiguous visible clips item; allowing content"
                } else if (match.screen == ReelsRules.Screen.UNCERTAIN) {
                    "Missing signals: ${match.missing}; retaining DM lock and allowing content"
                } else "DM lock: original reel allowed")
            }
        }
        if (match.screen == ReelsRules.Screen.DM_PLAYER) {
            val current = ReelsRules.fingerprint(root, viewport)
                ?: return Decision(diagnostic = "Missing signals: visible clips item; allowing content")
            state = State.DM_LOCKED
            fingerprint = current
            page = positions.lastOrNull()
            return Decision(diagnostic = "DM lock armed: original reel allowed")
        }
        val explore = ReelsRules.explore(root, viewport)
        exploreActive = blockExplore && (explore.state != null || searchSession)
        if (!blockExplore || ReelsRules.isHome(root, viewport)) {
            searchSession = false
            profileFromSearch = false
        } else if (explore.state == ReelsRules.ExploreState.SEARCH_ACTIVE) {
            searchSession = true
            profileFromSearch = false
        } else if (explore.state == ReelsRules.ExploreState.IDLE) {
            searchSession = false
            profileFromSearch = false
        } else if (searchSession && ReelsRules.isProfile(root, viewport)) {
            profileFromSearch = true
        }
        // DM origin and the single-reel lock above always take priority.
        if (match.screen == ReelsRules.Screen.EXPLORE_PLAYER) {
            if (!blockExplore || profileFromSearch) {
                state = State.IDLE
                return Decision(diagnostic = "${explore.diagnostic}; " +
                    if (!blockExplore) "disabled" else "profile post allowed")
            }
            if (state == State.BLOCK_SENT) return Decision(diagnostic = "${explore.diagnostic}; BACK already sent")
            state = State.BLOCK_SENT
            return Decision(Action.BACK, "${explore.diagnostic}; BACK once")
        }
        if (blockExplore && match.screen == ReelsRules.Screen.OTHER && explore.state in
            listOf(ReelsRules.ExploreState.IDLE, ReelsRules.ExploreState.SEARCH_ACTIVE)) {
            state = State.IDLE
            return Decision(diagnostic = explore.diagnostic, exploreCover = explore.cover)
        }
        if (profileFromSearch && match.screen == ReelsRules.Screen.PLAYER) {
            state = State.IDLE
            return Decision(diagnostic = "${explore.diagnostic}; profile post allowed")
        }
        if (match.screen in listOf(ReelsRules.Screen.REELS_TAB, ReelsRules.Screen.PLAYER)) {
            if (state == State.BLOCK_SENT) return Decision()
            state = State.BLOCK_SENT
            return if (match.screen == ReelsRules.Screen.REELS_TAB) {
                Decision(Action.HOME_OR_BACK, "Blocked Reels browsing: Home with BACK fallback")
            } else Decision(Action.BACK, "Blocked non-DM clips player: BACK")
        }
        state = State.IDLE
        fingerprint = null
        page = null
        var next = ReelsRules.feedGroups(root, viewport, feed, scrollDelta)
        if (feedScrolledToTop || (next?.atTop == true && feed != null && !feed!!.atTop)) refreshPending = true
        if (refreshPending && next?.atTop == true && feedContentChanged) {
            resetWall()
            feed = null
            groupIds = emptyList()
            skipped.clear()
            next = ReelsRules.feedGroups(root, viewport)
        }
        val ids = next?.groups?.map { group ->
            group.previousIndex?.let { groupIds.getOrNull(it) } ?: ++nextGroupId
        }.orEmpty()
        feed = next
        groupIds = ids
        skipped.retainAll(ids.toSet())
        if (next != null) {
            val wallDiagnostic = updateWall(next, ids, stopAtEnd, blockedGroupLimit)
            val currentWall = wall
            val wallRect = currentWall?.let { ReelsRules.wallRect(next, it.top) }
            val covers = next.groups.mapIndexedNotNull { index, group ->
                group.kind?.let { Cover(ids[index], group.rect, it) }
            }.mapNotNull { cover ->
                if (wallRect == null) cover else cover.rect.clip(next.area.copy(bottom = wallRect.top))
                    ?.let { cover.copy(rect = it) }
            }
            val backward = currentWall != null && wallRect != null &&
                ReelsRules.wallNeedsBackward(next, currentWall.top, currentWall.followedAbove) &&
                (lastBackward == null || now - lastBackward!! >= 400L)
            val diagnostic = "$wallDiagnostic SCROLL_BACKWARD=$backward; ${next.missing}"
            if (backward) {
                lastBackward = now
                return Decision(Action.SCROLL_BACKWARD, diagnostic, covers, wallRect)
            }
            val candidate = covers.firstOrNull { it.groupId !in skipped }
            if (wallRect == null && autoSkip && candidate != null && (lastSkip == null || now - lastSkip!! >= 500L)) {
                skipped.add(candidate.groupId)
                lastSkip = now
                return Decision(Action.SCROLL_FEED, "Auto-skip: scroll blocked Home group once; $diagnostic", covers)
            }
            return Decision(diagnostic = diagnostic, covers = covers, wall = wallRect)
        }
        resetWall()
        return Decision(diagnostic = if (match.screen == ReelsRules.Screen.UNCERTAIN) {
            "${explore.diagnostic}; Missing signals: ${match.missing}; allowing content"
        } else if (ReelsRules.isHome(root, viewport)) {
            "Wall consecutive=0 signal=none SCROLL_BACKWARD=false missing=visible feed list, tab bar, or usable feed bounds"
        } else "${explore.diagnostic}; ${if (blockExplore) "allowing content" else "disabled"}")
    }

    private fun updateWall(frame: ReelsRules.FeedFrame, ids: List<Long>, enabled: Boolean, limit: Int): String {
        if (!enabled) {
            resetWall()
            return "Wall consecutive=0 signal=disabled"
        }
        if (wallLimit != limit.coerceAtLeast(1)) resetWall()
        wallLimit = limit.coerceAtLeast(1)
        // Without proven movement, retaining a screen coordinate could cover an
        // unrelated post. Recompute from this sample's positive signals instead.
        val movement = frame.movement
        wall = if (movement != null) wall?.let { it.copy(top = it.top - movement) } else null
        val nextRuns = mutableMapOf<Long, Run>()
        var run: Run? = null
        var ordinaryAbove = false
        var maxCount = 0
        frame.groups.forEachIndexed { index, group ->
            if (ReelsRules.ordinaryPost(group)) ordinaryAbove = true
            if (group.kind == null) {
                run = null
            } else {
                val prior = if (movement != null) runs[ids[index]]?.let {
                    it.copy(top = it.top - movement)
                } else null
                val local = Run((run?.count ?: 0) + 1, run?.top ?: group.rect.top,
                    ordinaryAbove || run?.followedAbove == true)
                run = if (prior != null && prior.count >= local.count) prior.copy(
                    followedAbove = prior.followedAbove || local.followedAbove) else local
                nextRuns[ids[index]] = run!!
                maxCount = maxOf(maxCount, run!!.count)
                if (wall == null && run!!.count >= limit.coerceAtLeast(1)) {
                    wall = Wall(run!!.top, "blocked-run", run!!.followedAbove, run!!.count)
                }
            }
        }
        runs = nextRuns
        frame.markerTop?.let { top ->
            wall = Wall(top, "marker-id", (wall?.let { it.top <= top && it.followedAbove } == true) || frame.groups.any {
                it.rect.bottom <= top && ReelsRules.ordinaryPost(it)
            }, maxCount)
        }
        val current = wall
        return "Wall consecutive=${maxOf(maxCount, current?.count ?: 0)} signal=${current?.signal ?: "none"}" +
            if (current == null) " missing=direct-child marker or consecutive proven blocked groups" else
                " top=${current.top} followedAbove=${current.followedAbove} canScrollBackward=${frame.canScrollBackward}"
    }
}
