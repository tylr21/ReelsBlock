package com.example.reelsblock

import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class Stage4FeedTest {
    private val viewport = ReelsRules.Bounds(0, 0, 1440, 3168)

    // Stage 4 verifies per-post behavior with the independently tested wall off.
    private fun GuardStateMachine.stage4Step(
        root: ReelsRules.Node?, viewport: ReelsRules.Bounds, scrollDelta: Int? = null,
        autoSkip: Boolean = false, now: Long = 0L,
    ) = step(root, viewport, scrollDelta = scrollDelta, autoSkip = autoSkip, now = now, stopAtEnd = false)
    private fun dump(name: String): ReelsRules.Node {
        val stream = checkNotNull(javaClass.getResourceAsStream("/stage4/$name.xml"))
        val document = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        fun read(element: Element): ReelsRules.Node {
            val coords = Regex("-?\\d+").findAll(element.getAttribute("bounds")).map { it.value.toInt() }.toList()
            val bounds = if (coords.size == 4) ReelsRules.Bounds(coords[0], coords[1], coords[2], coords[3]) else viewport
            fun tags(attribute: String) = element.getAttribute(attribute).split(',')
                .filter { it.isNotEmpty() }.map { ReelsRules.Label.valueOf(it) }.toSet()
            return ReelsRules.Node(element.getAttribute("resource-id").ifEmpty { null }, bounds,
                element.getAttribute("selected") == "true",
                (0 until element.childNodes.length).mapNotNull { element.childNodes.item(it) as? Element }.map(::read),
                tags("labels"), tags("description-labels"))
        }
        return read(document.documentElement)
    }

    private fun ReelsRules.Node.edit(id: String, transform: (ReelsRules.Node) -> ReelsRules.Node): ReelsRules.Node =
        copy(children = children.map { it.edit(id, transform) }).let { if (it.id == id) transform(it) else it }

    private fun covers(tree: ReelsRules.Node) = GuardStateMachine().stage4Step(tree, viewport).covers

    @Test fun followedPostAndLikedReelBubbleAreAllowed() {
        val followed = dump("home_followed_post")
        assertTrue(covers(followed).isEmpty())
        val bubble = followed.edit(ReelsRules.FEED_HEADER) { it.copy(labels = setOf(ReelsRules.Label.LIKEDREEL)) }
        assertTrue(covers(bubble).isEmpty())
    }

    @Test fun adCoverUnionsAllRowsIncludingCtaButtonsAndFooter() {
        val tree = dump("home_sponsored_post")
        val frame = ReelsRules.feedGroups(tree, viewport)!!
        val cover = covers(tree).single()
        val group = frame.groups.single { it.kind != null }
        val expected = group.rows.mapNotNull { it.bounds.clip(ReelsRules.Bounds(0, 310, 1440, 2800)) }
            .reduce(ReelsRules.Bounds::union)
        assertEquals(expected, cover.rect)
        assertEquals(ReelsRules.Bounds(0, 310, 1440, 2800), cover.rect)
        assertTrue(cover.rect.bottom > 518)
    }

    @Test fun nextSuggestedHeaderDoesNotCoverUpperAllowedPostAndBoundsWinOverTreeOrder() {
        val tree = dump("home_followed_post_next_header")
        assertEquals(ReelsRules.Bounds(0, 2516, 1440, 2800), covers(tree).single().rect)
        val shuffled = tree.edit(ReelsRules.FEED_LIST) { it.copy(children = it.children.reversed()) }
        assertEquals(covers(tree).map { it.rect }, covers(shuffled).map { it.rect })
    }

    @Test fun adTailPersistsWithoutHeaderAndStopsAtNextHeader() {
        val machine = GuardStateMachine()
        val first = machine.stage4Step(dump("home_sponsored_post"), viewport).covers.single()
        // The captured CTA moved from y=2318 to y=256; no text identity is needed.
        val tailTree = dump("home_scan_04").edit(ReelsRules.PHOTO) {
            it.copy(labels = emptySet(), descriptionLabels = emptySet())
        }
        val tail = machine.stage4Step(tailTree, viewport).covers
        val ad = tail.single { it.groupId == first.groupId }
        assertEquals(ReelsRules.Bounds(0, 160, 1440, 844), ad.rect)
        assertEquals(ReelsRules.Bounds(0, 844, 1440, 2800), tail.single { it.groupId != ad.groupId }.rect)
        repeat(3) {
            assertEquals(ad, machine.stage4Step(tailTree, viewport).covers.first())
        }
    }

    @Test fun scrollDeltaPreservesAdTailWhenNoCompleteAnchorRowIsShared() {
        val machine = GuardStateMachine()
        val ad = machine.stage4Step(dump("home_scan_03"), viewport).covers.last()
        val tail = machine.stage4Step(dump("home_scan_04"), viewport, scrollDelta = 2062).covers
        assertEquals(ReelsRules.Bounds(0, 160, 1440, 844), tail.single { it.groupId == ad.groupId }.rect)
    }

    @Test fun visibleMediaNodeKeepsCoverWithoutScrollEventsAndRecyclingWithHeaderReclassifies() {
        // Source-node keys are runtime-only. Fake one stable view across the captured
        // header/media -> header-off-screen transition; no text identity is synthesized.
        val machine = GuardStateMachine()
        val before = dump("home_scan_03").edit(ReelsRules.PHOTO) {
            if (it.bounds.top == 1215) it.copy(sourceKey = 1 to 42) else it
        }
        val id = machine.stage4Step(before, viewport).covers.last().groupId
        val tail = dump("home_scan_04").edit(ReelsRules.PHOTO) {
            if (it.bounds.top == 160) it.copy(sourceKey = 1 to 42,
                labels = emptySet(), descriptionLabels = emptySet()) else it
        }
        repeat(3) {
            assertEquals(ReelsRules.Bounds(0, 160, 1440, 844),
                machine.stage4Step(tail, viewport).covers.single { it.groupId == id }.rect)
        }
        val followed = dump("home_followed_post").edit(ReelsRules.CAROUSEL_MEDIA) {
            it.copy(sourceKey = 1 to 42)
        }
        assertTrue(machine.stage4Step(followed, viewport).covers.isEmpty())
    }

    @Test fun inlineReelIsCoveredAndStoriesAndBarsAreOutsideEveryCover() {
        val tree = dump("home_inline_reel")
        val cover = covers(tree).single()
        assertEquals(ReelsRules.CoverKind.INLINE_REEL, cover.kind)
        assertEquals(ReelsRules.Bounds(0, 902, 1440, 2800), cover.rect)
        val exclusions = tree.descendants().filter {
            it.id in listOf(ReelsRules.STORIES, ReelsRules.TOP_BAR, ReelsRules.TAB_BAR)
        }.toList()
        for (node in exclusions) assertFalse(cover.rect.intersects(node.bounds))
        assertEquals(2, covers(dump("home_scan_01")).size)
    }

    @Test fun endMarkerStopsThePostAndIsNeverCovered() {
        val marker = ReelsRules.Node(ReelsRules.FEED_END, ReelsRules.Bounds(0, 2400, 1440, 2500))
        val tree = dump("home_sponsored_post").edit(ReelsRules.FEED_LIST) {
            it.copy(children = it.children + marker)
        }
        val cover = covers(tree).single()
        assertEquals(ReelsRules.Bounds(0, 310, 1440, 2400), cover.rect)
        assertFalse(cover.rect.intersects(marker.bounds))
    }

    @Test fun followButtonAloneIsEnoughOnlyInsideHomePostHeader() {
        val tree = dump("home_scan_08")
        assertEquals(ReelsRules.Bounds(0, 537, 1440, 2800), covers(tree).single().rect)
        assertTrue(covers(tree.edit(ReelsRules.FEED_HEADER) { it.copy(id = null) }).isEmpty())
        assertTrue(covers(tree.edit(ReelsRules.HOME_TAB) {
            it.copy(selected = false, children = it.children.map { child -> child.copy(selected = false) })
        }).isEmpty())
        val search = tree.copy(children = tree.children + ReelsRules.Node(ReelsRules.SEARCH_RESULTS, viewport))
        val dm = tree.copy(children = tree.children + ReelsRules.Node(ReelsRules.CLIPS_ROOT, viewport))
        assertTrue(covers(search).isEmpty())
        assertTrue(covers(dm).isEmpty())
        assertTrue(covers(dump("home_scan_06")).none { it.kind == ReelsRules.CoverKind.NON_FOLLOWED })
    }

    @Test fun changingSuggestedSubtitleAloneFailsOpenButFixedSuggestedAndMediaSponsoredBlock() {
        val tree = dump("home_scan_02").edit(ReelsRules.FOLLOW_BUTTON) { it.copy(id = null) }
        assertTrue(covers(tree).isEmpty())
        val suggested = tree.edit(ReelsRules.SECONDARY_LABEL) { it.copy(labels = setOf(ReelsRules.Label.SUGGESTED)) }
        assertEquals(1, covers(suggested).size)
        val sponsored = tree.edit(ReelsRules.VIDEO) { it.copy(descriptionLabels = setOf(ReelsRules.Label.SPONSORED)) }
        assertEquals(1, covers(sponsored).size)
        assertTrue(ReelsRules.descriptionLabels("Photo by placeholder. Sponsored.").contains(ReelsRules.Label.SPONSORED))
        assertFalse(ReelsRules.descriptionLabels("unsponsored").contains(ReelsRules.Label.SPONSORED))
        assertTrue(GuardStateMachine().stage4Step(tree, viewport).diagnostic.contains("missing"))
    }

    @Test fun invalidSignalsAndUncertainContinuityFailOpen() {
        val tree = dump("home_scan_08")
        for (bounds in listOf(ReelsRules.Bounds(0, 0, 0, 10), ReelsRules.Bounds(0, 0, -1, 10),
            ReelsRules.Bounds(1440, 0, 2880, 200))) {
            assertTrue(covers(tree.edit(ReelsRules.FOLLOW_BUTTON) { it.copy(bounds = bounds) }).isEmpty())
        }
        // With its Sponsored description removed, a fresh ad tail lacks a positive signal.
        val fresh = covers(dump("home_scan_04").edit(ReelsRules.PHOTO) {
            it.copy(labels = emptySet(), descriptionLabels = emptySet())
        })
        assertEquals(1, fresh.size)
        assertEquals(844, fresh.single().rect.top)
    }

    @Test fun autoSkipDefaultsOffSkipsEachGroupOnceAndHonorsCooldownAndReset() {
        val tree = dump("home_scan_01")
        val machine = GuardStateMachine()
        assertEquals(GuardStateMachine.Action.NONE, machine.stage4Step(tree, viewport).action)
        assertEquals(GuardStateMachine.Action.SCROLL_FEED, machine.stage4Step(tree, viewport, autoSkip = true, now = 1000).action)
        assertEquals(GuardStateMachine.Action.NONE, machine.stage4Step(tree, viewport, autoSkip = true, now = 1499).action)
        assertEquals(GuardStateMachine.Action.SCROLL_FEED, machine.stage4Step(tree, viewport, autoSkip = true, now = 1500).action)
        assertEquals(GuardStateMachine.Action.NONE, machine.stage4Step(tree, viewport, autoSkip = true, now = 2500).action)
        assertTrue(machine.stage4Step(null, viewport).covers.isEmpty())
        assertFalse(machine.needsForegroundCheck)
        assertEquals(GuardStateMachine.Action.SCROLL_FEED, machine.stage4Step(tree, viewport, autoSkip = true, now = 3000).action)
        machine.stage4Step(null, viewport)
        assertEquals(GuardStateMachine.Action.NONE, machine.stage4Step(tree, viewport, autoSkip = true, now = 3100).action)
        assertEquals(GuardStateMachine.Action.SCROLL_FEED, machine.stage4Step(tree, viewport, autoSkip = true, now = 3500).action)
    }

    @Test fun allScanCoversAreInsideFeedAndRemovedWhenLeavingHome() {
        val machine = GuardStateMachine()
        for (index in 1..8) {
            val tree = dump("home_scan_%02d".format(index))
            val frame = ReelsRules.feedGroups(tree, viewport)!!
            for (cover in machine.stage4Step(tree, viewport).covers) assertEquals(cover.rect, cover.rect.clip(frame.area))
        }
        assertTrue(machine.stage4Step(ReelsRules.Node(null, viewport), viewport).covers.isEmpty())
        assertFalse(machine.needsForegroundCheck)
    }
}
