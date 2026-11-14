package com.example.reelsblock

import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class Stage6ExploreTest {
    private val viewport = ReelsRules.Bounds(0, 0, 1440, 3168)

    // Fixtures contain captured structural fields only, never query/account/post text.
    private fun dump(name: String, stage: String = "stage6"): ReelsRules.Node {
        val stream = checkNotNull(javaClass.getResourceAsStream("/$stage/$name.xml"))
        val document = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        fun read(element: Element): ReelsRules.Node {
            val coords = Regex("-?\\d+").findAll(element.getAttribute("bounds")).map { it.value.toInt() }.toList()
            val bounds = if (coords.size == 4) ReelsRules.Bounds(coords[0], coords[1], coords[2], coords[3]) else viewport
            val className = element.getAttribute("class")
            return ReelsRules.Node(element.getAttribute("resource-id").ifEmpty { null }, bounds,
                element.getAttribute("selected") == "true",
                (0 until element.childNodes.length).mapNotNull { element.childNodes.item(it) as? Element }.map(::read),
                focused = element.getAttribute("focused") == "true",
                // UI Automator omits editable; its EditText class supplies the captured field type.
                editable = element.getAttribute("editable") == "true" || className == ReelsRules.EDIT_CLASS,
                className = className)
        }
        return read(document.documentElement)
    }

    private fun ReelsRules.Node.edit(id: String, transform: (ReelsRules.Node) -> ReelsRules.Node): ReelsRules.Node =
        copy(children = children.map { it.edit(id, transform) }).let { if (it.id == id) transform(it) else it }

    @Test fun idleCoversPillsAndGridBetweenActionBarAndTabsOnly() {
        val root = dump("stage6_explore_idle")
        val decision = GuardStateMachine().step(root, viewport)
        assertEquals(ReelsRules.Bounds(0, 384, 1440, 2800), decision.exploreCover)
        assertEquals(GuardStateMachine.Action.NONE, decision.action)
        assertTrue(decision.covers.isEmpty())
        assertNull(decision.wall)
        assertTrue(decision.diagnostic.contains("state=IDLE"))
        // The idle capture reports editable=true on a Button, which must stay idle.
        assertTrue(root.descendants().single { it.id == ReelsRules.SEARCH_EDIT }.editable)
    }

    @Test fun focusedTypedAndSubmittedSearchRemoveCoverAndReturnReappliesIt() {
        val machine = GuardStateMachine()
        val idle = dump("stage6_explore_idle")
        assertNotNull(machine.step(idle, viewport).exploreCover)
        for (name in listOf("stage6_search_focused", "stage6_search_typed", "stage6_search_results")) {
            val decision = machine.step(dump(name), viewport)
            assertNull(name, decision.exploreCover)
            assertTrue(name, decision.diagnostic.contains("state=SEARCH_ACTIVE"))
            assertEquals(GuardStateMachine.Action.NONE, decision.action)
            assertTrue(decision.covers.isEmpty())
        }
        assertEquals(ReelsRules.Bounds(0, 384, 1440, 2800), machine.step(idle, viewport).exploreCover)
    }

    @Test fun eachActiveSearchAlternativeNeedsTwoSignals() {
        val field = ReelsRules.Node(ReelsRules.SEARCH_EDIT, viewport,
            focused = true, editable = true, className = ReelsRules.EDIT_CLASS)
        assertEquals(ReelsRules.ExploreState.SEARCH_ACTIVE, ReelsRules.explore(field, viewport).state)
        for (partial in listOf(field.copy(focused = false), field.copy(editable = false),
            field.copy(className = "android.widget.Button"))) {
            assertNull(ReelsRules.explore(partial, viewport).state)
        }
        val captured = dump("stage6_search_results")
        val results = ReelsRules.Node(null, viewport, children = captured.descendants().filter {
            it.id in listOf(ReelsRules.SEARCH_PAGER, ReelsRules.SEARCH_RESULTS)
        }.map { it.copy(children = emptyList()) }.toList())
        assertNull(ReelsRules.explore(results.edit(ReelsRules.SEARCH_PAGER) { it.copy(id = null) }, viewport).state)
        assertNull(ReelsRules.explore(results.edit(ReelsRules.SEARCH_RESULTS) { it.copy(id = null) }, viewport).state)
    }

    @Test fun invalidOrMissingExploreSignalsFailOpenAndIconSelectionWorks() {
        val idle = dump("stage6_explore_idle")
        val iconSelected = idle.edit(ReelsRules.SEARCH_TAB) { it.copy(selected = false) }
        assertNotNull(GuardStateMachine().step(iconSelected, viewport).exploreCover)
        for (id in listOf(ReelsRules.EXPLORE_BAR, ReelsRules.SEARCH_TAB, ReelsRules.TAB_BAR)) {
            for (bounds in listOf(ReelsRules.Bounds(0, 160, -2880, 2800),
                ReelsRules.Bounds(0, 160, 0, 2800), ReelsRules.Bounds(1500, 160, 2000, 2800))) {
                val decision = GuardStateMachine().step(idle.edit(id) { it.copy(bounds = bounds) }, viewport)
                assertNull("$id $bounds", decision.exploreCover)
                assertTrue(decision.diagnostic.contains("missing="))
            }
        }
        assertFalse(ReelsRules.isHome(idle, viewport))
    }

    @Test fun photoReelAndSearchViewersBackOutExactlyOnceUntilClosed() {
        for ((name, stage) in listOf("explore_photo_viewer" to "stage6",
            "search_result_reel_viewer" to "stage6", "explore_post_viewer" to "stage3")) {
            val machine = GuardStateMachine()
            val viewer = dump(name, stage)
            machine.step(dump("stage6_explore_idle"), viewport)
            assertEquals(name, GuardStateMachine.Action.BACK, machine.step(viewer, viewport).action)
            repeat(5) { assertEquals(name, GuardStateMachine.Action.NONE, machine.step(viewer, viewport).action) }
            assertNotNull(machine.step(dump("stage6_explore_idle"), viewport).exploreCover)
            assertEquals(GuardStateMachine.Action.BACK, machine.step(viewer, viewport).action)
        }
    }

    @Test fun viewerNeedsVisibleTitleAndNoTabBar() {
        val viewer = dump("explore_photo_viewer")
        assertEquals(ReelsRules.ExploreState.VIEWER, ReelsRules.explore(viewer, viewport).state)
        assertNull(ReelsRules.explore(viewer.edit(ReelsRules.VIEWER_TITLE) { it.copy(id = null) }, viewport).state)
        assertNull(ReelsRules.explore(viewer.copy(children = viewer.children +
            ReelsRules.Node(ReelsRules.TAB_BAR, ReelsRules.Bounds(0, 2800, 1440, 2992))), viewport).state)
    }

    @Test fun dmOriginAndPartialDmChromeWinEvenWithExploreTitle() {
        val dm = dump("dm_shared_reel", "stage3")
        val withTitle = dm.edit(ReelsRules.CLIPS_ROOT) { it.copy(children = it.children +
            ReelsRules.Node(ReelsRules.VIEWER_TITLE, ReelsRules.Bounds(256, 160, 1216, 384))) }
        val machine = GuardStateMachine()
        machine.step(dump("stage6_search_results"), viewport)
        repeat(5) { assertEquals(GuardStateMachine.Action.NONE, machine.step(withTitle, viewport).action) }
        val partial = withTitle.edit(ReelsRules.DM_REPLY) { it.copy(id = null) }
        assertEquals(GuardStateMachine.Action.NONE, GuardStateMachine().step(partial, viewport).action)
    }

    @Test fun searchProfilesAndTheirPostsAreAllowedAndReturningToResultsClearsOrigin() {
        val machine = GuardStateMachine()
        machine.step(dump("stage6_search_results"), viewport)
        for (name in listOf("profile_from_search", "profile_post_viewer", "profile_from_search")) {
            val decision = machine.step(dump(name), viewport)
            assertEquals(name, GuardStateMachine.Action.NONE, decision.action)
            assertNull(decision.exploreCover)
            assertTrue(decision.covers.isEmpty())
        }
        // Same clips chrome from a profile retains its allowed navigation origin.
        assertEquals(GuardStateMachine.Action.NONE, machine.step(dump("search_result_reel_viewer"), viewport).action)
        machine.step(dump("stage6_search_results"), viewport)
        assertEquals(GuardStateMachine.Action.BACK, machine.step(dump("search_result_reel_viewer"), viewport).action)
    }

    @Test fun followInSearchResultsNeverActivatesHomeRulesEvenWithStaleHomeSelection() {
        val results = dump("stage6_search_results").copy(children = listOf(
            dump("stage6_search_results"),
            dump("home_sponsored_post", "stage4"),
            ReelsRules.Node(ReelsRules.FOLLOW_BUTTON, ReelsRules.Bounds(500, 1000, 800, 1200))))
        val decision = GuardStateMachine().step(results, viewport)
        assertTrue(decision.covers.isEmpty())
        assertNull(decision.wall)
        assertNull(decision.exploreCover)
        assertEquals(GuardStateMachine.Action.NONE, decision.action)
    }

    @Test fun settingOffDisablesExploreIncludingViewerBackAndRemovesExistingCover() {
        val machine = GuardStateMachine()
        assertNotNull(machine.step(dump("stage6_explore_idle"), viewport).exploreCover)
        for (name in listOf("stage6_explore_idle", "stage6_search_focused", "stage6_search_typed",
            "stage6_search_results", "explore_photo_viewer", "search_result_reel_viewer")) {
            val decision = machine.step(dump(name), viewport, blockExplore = false)
            assertEquals(name, GuardStateMachine.Action.NONE, decision.action)
            assertNull(decision.exploreCover)
            assertTrue(decision.covers.isEmpty())
            assertNull(decision.wall)
        }
        assertFalse(machine.needsForegroundCheck)
        assertNotNull(machine.step(dump("stage6_explore_idle"), viewport).exploreCover)
        machine.step(null, viewport)
        assertFalse(machine.needsForegroundCheck)
    }
}
