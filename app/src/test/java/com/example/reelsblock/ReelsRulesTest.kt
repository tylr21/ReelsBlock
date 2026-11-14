package com.example.reelsblock

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelsRulesTest {
    @Test
    fun debugLabelsMatchFixedStringsInTextOrDescription() {
        for (label in ReelsRules.Label.entries) {
            assertTrue(ReelsRules.labelMatches(label, label.value, null))
            assertTrue(ReelsRules.labelMatches(label, null, label.value.lowercase()))
            assertFalse(ReelsRules.labelMatches(label, null, null))
            assertEquals(label.allowSuffix, ReelsRules.labelMatches(label, "${label.value} someone", null))
            assertFalse(ReelsRules.labelMatches(label, "prefix ${label.value}", null))
            assertEquals(label.name, ReelsRules.debugDescription(label.value))
        }
        assertFalse(ReelsRules.labelMatches(ReelsRules.Label.AD, "Adventure", "private caption"))
        assertEquals("<redacted>", ReelsRules.debugDescription("private caption\nFollow"))
        assertEquals("<none>", ReelsRules.debugDescription(null))
        assertEquals("<none>", ReelsRules.debugDescription(""))
    }

    @Test
    fun fakeNodesUseDescriptionsInTheSameLabelOutputAsTheDump() {
        data class FakeNode(val text: CharSequence? = null, val desc: CharSequence?)
        val ad = FakeNode(desc = "Ad")
        assertTrue(ReelsRules.debugLabelTags(ad.text, ad.desc).split(' ').contains("lbl=AD=true"))
        val follow = FakeNode(desc = "Follow private_account")
        assertTrue(ReelsRules.debugLabelTags(follow.text, follow.desc).contains("lbl=FOLLOW=true"))
        assertEquals("FOLLOW", ReelsRules.debugDescription(follow.desc))
        assertFalse(ReelsRules.debugLabelTags(follow.text, follow.desc).contains("private_account"))
        assertFalse(ReelsRules.labelMatches(ReelsRules.Label.FOLLOW, null, "Following private_account"))
    }

    @Test
    fun additionalTagsAndAliasesAreEmittedWithoutPrivateSuffixes() {
        val examples = mapOf(
            "You're all caught up" to "CAUGHTUP",
            "You\u2019re all caught up" to "CAUGHTUP",
            "View older posts" to "OLDERPOSTS",
            "Reel by private_account" to "REELBY",
            "Photo by private_account" to "PHOTOBY",
            "Liked reel by private_account" to "LIKEDREEL",
            "Turn sound on" to "SOUND",
            "Search and explore" to "SEARCH",
            "Suggested for you" to "SUGGESTED_FOR_YOU",
        )
        for ((description, tag) in examples) {
            val output = ReelsRules.debugLabelTags(null, description)
            assertTrue(output.split(' ').contains("lbl=$tag=true"))
            assertFalse(output.contains("private_account"))
        }
    }

    @Test
    fun acceptsOnlyTheThreeConfiguredInstagramEvents() {
        val allowedEvents = listOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
        )
        for (type in allowedEvents) {
            assertTrue(ReelsRules.acceptsEvent("com.instagram.android", type))
            assertFalse(ReelsRules.acceptsEvent("com.example.other", type))
            assertFalse(ReelsRules.acceptsEvent("com.instagram.android.other", type))
            assertFalse(ReelsRules.acceptsEvent(null, type))
        }
        assertFalse(ReelsRules.acceptsEvent("com.instagram.android", AccessibilityEvent.TYPE_VIEW_CLICKED))
        assertFalse(ReelsRules.acceptsEvent("com.instagram.android", AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED))
        assertFalse(ReelsRules.acceptsEvent("com.instagram.android", 0))
    }
}
