package com.example.reelsblock

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GuardSettingsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun consentAndDiagnosticsAreOffByDefault() {
        runBlocking {
            val job = SupervisorJob()
            try {
                val store = openStore(CoroutineScope(job + Dispatchers.IO))
                assertEquals(GuardSettings(), store.settings.first())
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun settingsSurviveReopeningAndDiagnosticsCanBeDisabledWithoutLosingConsent() {
        runBlocking {
            val firstJob = SupervisorJob()
            try {
                val store = openStore(CoroutineScope(firstJob + Dispatchers.IO))
                store.acceptDisclosure()
                store.setDebugDiagnostics(true)
                store.setAutoSkipBlockedItems(true)
                store.setStopAtEndOfFollowedContent(false)
                store.setConsecutiveBlockedGroups(5)
                store.setBlockExplore(false)
                store.setShowToast(true)
            } finally {
                firstJob.cancelAndJoin()
            }

            val secondJob = SupervisorJob()
            try {
                val store = openStore(CoroutineScope(secondJob + Dispatchers.IO))
                assertEquals(GuardSettings(disclosureAccepted = true, debugDiagnostics = true,
                    autoSkipBlockedItems = true, stopAtEndOfFollowedContent = false,
                    consecutiveBlockedGroups = 5, blockExplore = false, showToast = true), store.settings.first())
                store.setShowToast(false)
                assertFalse(store.settings.first().showToast)
                store.setBlockExplore(true)
                assertTrue(store.settings.first().blockExplore)
                store.setStopAtEndOfFollowedContent(true)
                assertTrue(store.settings.first().stopAtEndOfFollowedContent)
                assertEquals(5, store.settings.first().consecutiveBlockedGroups)
                store.setAutoSkipBlockedItems(false)
                assertFalse(store.settings.first().autoSkipBlockedItems)
                store.setDebugDiagnostics(false)
                assertTrue(store.settings.first().disclosureAccepted)
                assertFalse(store.settings.first().debugDiagnostics)
            } finally {
                secondJob.cancelAndJoin()
            }
        }
    }

    @Test
    fun invalidWallThresholdDoesNotOverwriteTheSavedValue() = runBlocking {
        val job = SupervisorJob()
        try {
            val store = openStore(CoroutineScope(job + Dispatchers.IO))
            store.setConsecutiveBlockedGroups(4)
            try {
                store.setConsecutiveBlockedGroups(0)
                throw AssertionError("Zero must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertEquals(4, store.settings.first().consecutiveBlockedGroups)
        } finally { job.cancelAndJoin() }
    }

    private fun openStore(scope: CoroutineScope) = GuardSettingsStore(
        PreferenceDataStoreFactory.create(scope = scope) {
            File(temporaryFolder.root, "settings.preferences_pb")
        },
    )
}
