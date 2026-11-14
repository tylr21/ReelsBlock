package com.example.reelsblock

import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class Stage5WallTest {
    private val viewport = ReelsRules.Bounds(0, 0, 1440, 3168)
    private fun dump(name: String = "home_end_of_feed", stage: Int = 5): ReelsRules.Node {
        val stream = checkNotNull(javaClass.getResourceAsStream("/stage$stage/$name.xml"))
        val document = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        fun read(e: Element): ReelsRules.Node {
            val b = Regex("-?\\d+").findAll(e.getAttribute("bounds")).map { it.value.toInt() }.toList()
            fun tags(key: String) = e.getAttribute(key).split(',').filter { it.isNotEmpty() }
                .map { ReelsRules.Label.valueOf(it) }.toSet()
            return ReelsRules.Node(e.getAttribute("resource-id").ifEmpty { null },
                if (b.size == 4) ReelsRules.Bounds(b[0], b[1], b[2], b[3]) else viewport,
                e.getAttribute("selected") == "true",
                (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? Element }.map(::read),
                tags("labels"), tags("description-labels"))
        }
        return read(document.documentElement)
    }
    private fun ReelsRules.Node.edit(id: String, change: (ReelsRules.Node) -> ReelsRules.Node): ReelsRules.Node =
        copy(children = children.map { it.edit(id, change) }).let { if (it.id == id) change(it) else it }
    private fun ReelsRules.Node.move(delta: Int): ReelsRules.Node = copy(
        bounds = bounds.copy(top = bounds.top + delta, bottom = bounds.bottom + delta),
        children = children.map { it.move(delta) })
    private fun noMarker(tree: ReelsRules.Node = dump()) = tree.edit(ReelsRules.FEED_END) { it.copy(id = null) }

    // Repeat the captured suggested header/media row shapes at explicit spatial
    // positions. This is a fake multi-post viewport, not an additional capture.
    private fun runTree(count: Int, ordinaryBetween: Boolean = false): ReelsRules.Node {
        val template = dump("home_scan_02", 4)
        val list = template.descendants().single { it.id == ReelsRules.FEED_LIST }
        val post = list.children.single { row -> row.descendants().any { it.id == ReelsRules.FEED_HEADER } }
        val groups = (0 until count).map { index ->
            val top = 400 + index * 650
            // Scale the captured row vertically, preserving its actual IDs/labels.
            fun scale(n: ReelsRules.Node): ReelsRules.Node = n.copy(
                bounds = n.bounds.copy(top = top + (n.bounds.top - 344) / 4,
                    bottom = top + (n.bounds.bottom - 344) / 4), children = n.children.map(::scale))
            scale(post).let { row ->
                if (ordinaryBetween && index == 1) row.edit(ReelsRules.FOLLOW_BUTTON) { it.copy(id = null) }
                else row
            }
        }
        return template.edit(ReelsRules.FEED_LIST) { it.copy(children = groups, canScrollBackward = true) }
    }

    @Test fun markerImmediatelyCoversToTabBarIncludingOlderPostsAndExcludesStories() {
        val tree = dump()
        val result = GuardStateMachine().step(tree, viewport)
        assertEquals(ReelsRules.Bounds(0, 902, 1440, 2800), result.wall)
        assertTrue(result.diagnostic.contains("signal=marker-id"))
        assertFalse(tree.descendants().any { ReelsRules.Label.CAUGHTUP in it.labels })
        tree.descendants().filter { ReelsRules.Label.OLDERPOSTS in it.labels }.forEach { older ->
            assertEquals(older.bounds, older.bounds.clip(result.wall!!))
        }
        tree.descendants().filter { it.id in listOf(ReelsRules.STORIES, ReelsRules.TAB_BAR) }
            .forEach { assertFalse(result.wall!!.intersects(it.bounds)) }
        assertTrue(result.covers.isEmpty())
        val reversed = tree.edit(ReelsRules.FEED_LIST) { it.copy(children = it.children.reversed()) }
        assertEquals(result.wall, GuardStateMachine().step(reversed, viewport).wall)
    }

    @Test fun threeConsecutiveGroupsWallAtFirstTopButTwoOnlyGetPostCovers() {
        val two = GuardStateMachine().step(runTree(2), viewport)
        assertNull(two.wall)
        assertEquals(2, two.covers.size)
        val three = GuardStateMachine().step(runTree(3), viewport)
        assertEquals(ReelsRules.Bounds(0, 400, 1440, 2800), three.wall)
        assertTrue(three.diagnostic.contains("consecutive=3 signal=blocked-run"))
        assertTrue(three.covers.isEmpty())
        assertNull(GuardStateMachine().step(runTree(3, ordinaryBetween = true), viewport).wall)
        assertNotNull(GuardStateMachine().step(runTree(2), viewport, blockedGroupLimit = 2).wall)
        val machine = GuardStateMachine()
        assertNotNull(machine.step(runTree(3), viewport).wall)
        assertNull(machine.step(runTree(3), viewport, blockedGroupLimit = 4).wall)
    }

    @Test fun repeatedFramesDoNotCountSamePostAgain() {
        val machine = GuardStateMachine()
        repeat(6) { assertNull(machine.step(runTree(2), viewport, now = it * 500L).wall) }
    }

    @Test fun blockedRunSpansCapturedScrollFramesAndKeepsFirstGroupsProjectedTop() {
        val machine = GuardStateMachine()
        assertNull(machine.step(dump("home_scan_01", 4), viewport).wall)
        assertNull(machine.step(dump("home_scan_02", 4), viewport, scrollDelta = 1530).wall)
        val third = machine.step(dump("home_scan_03", 4), viewport, scrollDelta = 1700)
        assertNotNull(third.wall)
        assertEquals(160, third.wall!!.top)
        assertTrue(third.diagnostic.contains("signal=blocked-run"))
        assertTrue(third.diagnostic.contains("consecutive=3"))
    }

    @Test fun latchedMarkerMovesWithScrollEvenAfterMarkerLeavesVisibleTree() {
        val machine = GuardStateMachine()
        assertNotNull(machine.step(dump(), viewport).wall)
        val next = noMarker().edit(ReelsRules.FEED_LIST) { it.copy(children = it.children.map { row -> row.move(-300) }) }
        val result = machine.step(next, viewport, scrollDelta = 300)
        assertEquals(ReelsRules.Bounds(0, 602, 1440, 2800), result.wall)
        assertTrue(result.diagnostic.contains("signal=marker-id"))
    }

    @Test fun refreshWhileAlreadyAtTopClearsRunHistoryOnExplicitTopScrollAndContentChange() {
        val machine = GuardStateMachine()
        val top = dump().edit(ReelsRules.FEED_LIST) { it.copy(canScrollBackward = false) }
        assertNotNull(machine.step(top, viewport).wall)
        assertNull(machine.step(noMarker(top), viewport, scrollDelta = 0,
            feedContentChanged = true, feedScrolledToTop = true).wall)
    }

    @Test fun noFollowedPostAboveNeverScrollsEvenWhenBackwardIsAvailable() {
        for (backward in listOf(false, true, null)) {
            val tree = dump().edit(ReelsRules.FEED_LIST) { it.copy(canScrollBackward = backward) }
            val machine = GuardStateMachine()
            repeat(5) {
                val decision = machine.step(tree, viewport, autoSkip = true, now = it * 500L)
                assertNotNull(decision.wall)
                assertEquals(GuardStateMachine.Action.NONE, decision.action)
            }
        }
    }

    private fun mixedMarker(top: Int = 1800, backward: Boolean? = true): ReelsRules.Node {
        val ordinary = dump("home_followed_post", 4).descendants().single { it.id == ReelsRules.FEED_LIST }
        // Captured ordinary header plus media, clipped to the area before marker.
        val rows = ordinary.children.take(2).map { row ->
            fun trim(n: ReelsRules.Node): ReelsRules.Node = n.copy(
                bounds = n.bounds.copy(bottom = minOf(n.bounds.bottom, top)), children = n.children.map(::trim))
            trim(row.move(224))
        }
        val marker = dump().descendants().single { it.id == ReelsRules.FEED_END }.move(top - 902)
        return dump().edit(ReelsRules.FEED_LIST) {
            it.copy(children = rows + marker, canScrollBackward = backward)
        }
    }

    @Test fun followedAboveAndSixtyPercentLineRequireExactlyOneBackwardPerCooldown() {
        val machine = GuardStateMachine()
        assertEquals(GuardStateMachine.Action.NONE, machine.step(mixedMarker(), viewport, now = 900).action)
        val risen = mixedMarker(1300)
        assertEquals(GuardStateMachine.Action.SCROLL_BACKWARD, machine.step(risen, viewport, now = 1000).action)
        assertEquals(GuardStateMachine.Action.NONE, machine.step(risen, viewport, now = 1399).action)
        assertEquals(GuardStateMachine.Action.SCROLL_BACKWARD, machine.step(risen, viewport, now = 1400).action)
        assertEquals(GuardStateMachine.Action.NONE, machine.step(mixedMarker(), viewport, now = 1800).action)
        assertEquals(GuardStateMachine.Action.NONE,
            GuardStateMachine().step(mixedMarker(1300, false), viewport).action)
        assertEquals(GuardStateMachine.Action.NONE,
            GuardStateMachine().step(mixedMarker(1300, null), viewport).action)
    }

    @Test fun leavingHomeAndForegroundResetThenRecompute() {
        val machine = GuardStateMachine()
        assertNotNull(machine.step(dump(), viewport).wall)
        val otherTab = dump().edit(ReelsRules.HOME_TAB) { it.copy(selected = false,
            children = it.children.map { child -> child.copy(selected = false) }) }
        assertNull(machine.step(otherTab, viewport).wall)
        assertNull(machine.step(noMarker(), viewport).wall)
        assertNotNull(machine.step(dump(), viewport).wall)
        assertNull(machine.step(null, viewport).wall)
        assertNull(machine.step(noMarker(), viewport).wall)
        assertNotNull(machine.step(dump(), viewport).wall)
    }

    @Test fun returningToTopAndContentChangeResetWallBeforeRecomputing() {
        val machine = GuardStateMachine()
        val away = dump().edit(ReelsRules.FEED_LIST) { it.copy(canScrollBackward = true) }
        assertNotNull(machine.step(away, viewport).wall)
        val refreshed = noMarker().edit(ReelsRules.FEED_LIST) { it.copy(canScrollBackward = false) }
        val result = machine.step(refreshed, viewport, scrollDelta = 0, feedContentChanged = true)
        assertNull(result.wall)
        assertNotNull(machine.step(dump(), viewport).wall)
    }

    @Test fun settingOffDisablesMarkerAndRunWallsAndResetsLatch() {
        val machine = GuardStateMachine()
        assertNotNull(machine.step(dump(), viewport).wall)
        assertNull(machine.step(dump(), viewport, stopAtEnd = false).wall)
        assertNull(machine.step(noMarker(), viewport).wall)
        assertNull(machine.step(runTree(3), viewport, stopAtEnd = false).wall)
    }

    @Test fun invalidOffscreenNestedOrMissingSignalsDoNotWall() {
        for (bounds in listOf(ReelsRules.Bounds(0, 0, 0, 100), ReelsRules.Bounds(0, 100, 1440, 50),
            ReelsRules.Bounds(1440, 0, 2880, 100))) {
            assertNull(GuardStateMachine().step(dump().edit(ReelsRules.FEED_END) {
                it.copy(bounds = bounds)
            }, viewport).wall)
        }
        val nested = dump().edit(ReelsRules.FEED_LIST) { list -> list.copy(children = list.children.map {
            if (it.id == ReelsRules.FEED_END) ReelsRules.Node(null, it.bounds, children = listOf(it)) else it
        }) }
        assertNull(GuardStateMachine().step(nested, viewport).wall)
        val missingTab = dump().edit(ReelsRules.TAB_BAR) { it.copy(id = null) }
        assertNull(GuardStateMachine().step(missingTab, viewport).wall)
        assertTrue(GuardStateMachine().step(noMarker(), viewport).diagnostic.contains("missing="))
    }

    @Test fun scanEvidenceCannotEstablishEndFromAnIsolatedBlockedGroup() {
        for (i in 1..8) assertNull(GuardStateMachine().step(dump("home_scan_%02d".format(i), 4), viewport).wall)
    }
}
