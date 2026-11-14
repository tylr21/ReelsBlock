package com.example.reelsblock

import android.view.accessibility.AccessibilityEvent
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class Stage3RulesTest {
    private val viewport = ReelsRules.Bounds(0, 0, 1440, 3168)
    private val thread = ReelsRules.Node(null, viewport)

    private fun dump(name: String): ReelsRules.Node {
        val stream = checkNotNull(javaClass.getResourceAsStream("/stage3/$name.xml"))
        val document = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        fun read(element: Element): ReelsRules.Node {
            val coords = Regex("-?\\d+").findAll(element.getAttribute("bounds")).map { it.value.toInt() }.toList()
            val bounds = if (coords.size == 4) ReelsRules.Bounds(coords[0], coords[1], coords[2], coords[3]) else viewport
            return ReelsRules.Node(
                element.getAttribute("resource-id").ifEmpty { null }, bounds,
                element.getAttribute("selected") == "true",
                (0 until element.childNodes.length).mapNotNull { element.childNodes.item(it) as? Element }.map(::read),
            )
        }
        return read(document.documentElement)
    }

    private fun ReelsRules.Node.change(id: String, edit: (ReelsRules.Node) -> ReelsRules.Node): ReelsRules.Node =
        copy(children = children.map { it.change(id, edit) }).let { if (it.id == id) edit(it) else it }

    @Test fun reelsTabAndBrowsingScreenAreBlocked() {
        val reels = dump("reels_tab")
        assertEquals(GuardStateMachine.Action.HOME_OR_BACK, GuardStateMachine().step(reels, viewport).action)
        // The tab's icon may hold the selected flag instead of the tab container.
        val childSelected = reels.change(ReelsRules.REELS_TAB) { it.copy(selected = false) }
        assertEquals(ReelsRules.Screen.REELS_TAB, ReelsRules.classify(childSelected, viewport).screen)
        // The captured Reels header + player are sufficient without any tab selection.
        val browsing = reels.change(ReelsRules.REELS_TAB) {
            it.copy(selected = false, children = it.children.map { child -> child.copy(selected = false) })
        }
        assertEquals(ReelsRules.Screen.REELS_TAB, ReelsRules.classify(browsing, viewport).screen)
    }

    @Test fun exploreAndGenericNonDmPlayersAreBlocked() {
        val explore = dump("explore_post_viewer")
        assertEquals(ReelsRules.Screen.EXPLORE_PLAYER, ReelsRules.classify(explore, viewport).screen)
        assertEquals(GuardStateMachine.Action.BACK, GuardStateMachine().step(explore, viewport).action)
        val generic = explore.change(ReelsRules.EXPLORE_COMPOSER) { it.copy(id = null) }
            .change(ReelsRules.VIEWER_TITLE) { it.copy(id = null) }
        assertEquals(ReelsRules.Screen.PLAYER, ReelsRules.classify(generic, viewport).screen)
        assertEquals(GuardStateMachine.Action.BACK, GuardStateMachine().step(generic, viewport).action)
    }

    @Test fun playerRequiresMultipleSignalsAndPartialDmChromeFailsOpen() {
        val partial = ReelsRules.Node(ReelsRules.CLIPS_ROOT, viewport)
        assertEquals(ReelsRules.Screen.UNCERTAIN, ReelsRules.classify(partial, viewport).screen)
        val dm = dump("dm_shared_reel").change(ReelsRules.DM_REPLY) { it.copy(id = null) }
        val decision = GuardStateMachine().step(dm, viewport)
        assertEquals(GuardStateMachine.Action.NONE, decision.action)
        assertTrue(decision.diagnostic.contains(ReelsRules.DM_REPLY))
    }

    @Test fun dmStructureWinsOverGenericClipsAndFollowControls() {
        val dm = dump("dm_shared_reel")
        assertEquals(ReelsRules.Screen.DM_PLAYER, ReelsRules.classify(dm, viewport).screen)
        assertEquals(GuardStateMachine.Action.NONE, GuardStateMachine().step(dm, viewport).action)
        // The after-swipe dump contains a Follow control. No follow status or text
        // participates in the decision; the first DM reel is allowed for any sender.
        val withMoreChildren = dm.copy(children = dm.children + dump("explore_post_viewer"))
        assertEquals(ReelsRules.Screen.DM_PLAYER, ReelsRules.classify(withMoreChildren, viewport).screen)
        val editOnly = dm.change(ReelsRules.DM_COMPOSER) { it.copy(id = null) }
        assertEquals(ReelsRules.Screen.DM_PLAYER, ReelsRules.classify(editOnly, viewport).screen)
    }

    @Test fun actualSwipeFingerprintChangeSendsExactlyOneBack() {
        val before = dump("dm_reel_before_swipe")
        val after = dump("dm_reel_after_swipe")
        val machine = GuardStateMachine()
        assertNotEquals(ReelsRules.fingerprint(before, viewport), ReelsRules.fingerprint(after, viewport))
        assertEquals(GuardStateMachine.Action.NONE, machine.step(before, viewport).action)
        val event = ReelsRules.PagerPosition(1, 1, 0)
        assertEquals(GuardStateMachine.Action.BACK, machine.step(after, viewport, listOf(event, event)).action)
        repeat(5) {
            assertEquals(GuardStateMachine.Action.NONE, machine.step(after, viewport, listOf(event)).action)
        }
    }

    @Test fun structuralChangeWithoutScrollEventStillExitsOnce() {
        val machine = GuardStateMachine()
        machine.step(dump("dm_reel_before_swipe"), viewport)
        assertEquals(GuardStateMachine.Action.BACK, machine.step(dump("dm_reel_after_swipe"), viewport).action)
        assertEquals(GuardStateMachine.Action.NONE, machine.step(dump("dm_reel_after_swipe"), viewport).action)
    }

    @Test fun pagerPositionChangeExitsEvenWhenRecycledItemKeepsItsPath() {
        val dm = dump("dm_shared_reel")
        val machine = GuardStateMachine()
        val initial = ReelsRules.PagerPosition(0, 0, 0)
        machine.step(dm, viewport, listOf(initial))
        assertEquals(GuardStateMachine.Action.NONE, machine.step(dm, viewport, listOf(initial)).action)
        assertEquals(GuardStateMachine.Action.BACK,
            machine.step(dm, viewport, listOf(ReelsRules.PagerPosition(1, 1, 0), initial)).action)
        assertEquals(GuardStateMachine.Action.NONE, machine.step(dm, viewport).action)
    }

    @Test fun secondSentReelAfterThreadReturnIsAllowed() {
        val before = dump("dm_reel_before_swipe")
        val after = dump("dm_reel_after_swipe")
        val machine = GuardStateMachine()
        machine.step(before, viewport)
        assertEquals(GuardStateMachine.Action.BACK, machine.step(after, viewport).action)
        machine.step(thread, viewport)
        assertFalse(machine.needsForegroundCheck)
        // Represent another sent reel at the changed item position, with the
        // original DM chrome restored by returning to the thread and reopening.
        val pager = after.descendants().first { it.id == ReelsRules.CLIPS_PAGER }
        val second = before.change(ReelsRules.CLIPS_PAGER) { pager }
        assertEquals(GuardStateMachine.Action.NONE, machine.step(second, viewport).action)
        assertTrue(machine.needsForegroundCheck)
        assertEquals(GuardStateMachine.Action.NONE, machine.step(second, viewport).action)
    }

    @Test fun foregroundLossAndPlayerCloseResetTheLock() {
        val dm = dump("dm_shared_reel")
        val machine = GuardStateMachine()
        machine.step(dm, viewport)
        machine.step(null, viewport)
        assertFalse(machine.needsForegroundCheck)
        assertEquals(GuardStateMachine.Action.NONE, machine.step(dm, viewport).action)
        machine.step(thread, viewport)
        assertFalse(machine.needsForegroundCheck)
        assertEquals(GuardStateMachine.Action.NONE, machine.step(dm, viewport).action)
    }

    @Test fun invalidAndOffscreenBoundsNeverSupplyRuleSignals() {
        for (bounds in listOf(
            ReelsRules.Bounds(0, 160, -2880, 2800), ReelsRules.Bounds(0, 0, 0, 100),
            ReelsRules.Bounds(0, 100, 100, 100), ReelsRules.Bounds(1440, 160, 2880, 2800),
            ReelsRules.Bounds(-1440, 160, 0, 2800), ReelsRules.Bounds(0, 3168, 1440, 4000),
        )) {
            val node = ReelsRules.Node(ReelsRules.REELS_TAB, bounds, selected = true)
            assertEquals(ReelsRules.Screen.OTHER, ReelsRules.classify(node, viewport).screen)
            val dm = dump("dm_shared_reel").change(ReelsRules.DM_SENDER) { it.copy(bounds = bounds) }
            assertEquals(ReelsRules.Screen.UNCERTAIN, ReelsRules.classify(dm, viewport).screen)
        }
    }

    @Test fun unrelatedAndOffscreenScrollEventsDoNotChangeTheFingerprint() {
        assertNull(ReelsRules.pagerPosition(ReelsRules.HOME_TAB, viewport, viewport,
            AccessibilityEvent.TYPE_VIEW_SCROLLED, 1, 1, 0))
        assertNull(ReelsRules.pagerPosition(ReelsRules.CLIPS_PAGER, viewport, viewport,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, -1, -1, 0))
        assertNull(ReelsRules.pagerPosition(ReelsRules.CLIPS_PAGER, ReelsRules.Bounds(0, 0, 0, 0), viewport,
            AccessibilityEvent.TYPE_VIEW_SCROLLED, 1, 1, 0))
    }

    @Test fun offscreenViewerCannotRetainLockThroughItsVisibleStaleChildren() {
        val hidden = dump("dm_shared_reel").change(ReelsRules.CLIPS_ROOT) {
            it.copy(bounds = ReelsRules.Bounds(0, 160, -2880, 2800))
        }
        assertFalse(ReelsRules.hasPlayer(hidden, viewport))
        assertNull(ReelsRules.fingerprint(hidden, viewport))
    }
}
