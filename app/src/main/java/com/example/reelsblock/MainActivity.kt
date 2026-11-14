package com.example.reelsblock

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.reelsblock.ui.theme.ReelsBlockTheme
import java.io.IOException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val settingsStore by lazy { GuardSettingsStore(applicationContext.guardDataStore) }
    private var serviceEnabled by mutableStateOf(false)
    private var errorMessage by mutableStateOf<Int?>(null)
    private var savingConsent by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsStore.settings.collectAsState(initial = null)
            ReelsBlockTheme {
                GuardScreen(serviceEnabled, settings, errorMessage, savingConsent,
                    onEnable = ::acceptAndOpenSettings,
                    onOpenSettings = ::openAccessibilitySettings,
                    onShowToastChanged = { saveSetting { settingsStore.setShowToast(it) } },
                    onDebugChanged = { saveSetting { settingsStore.setDebugDiagnostics(it) } },
                    onAutoSkipChanged = { saveSetting { settingsStore.setAutoSkipBlockedItems(it) } },
                    onStopAtEndChanged = { saveSetting { settingsStore.setStopAtEndOfFollowedContent(it) } },
                    onBlockedGroupsChanged = { saveSetting { settingsStore.setConsecutiveBlockedGroups(it) } },
                    onBlockExploreChanged = { saveSetting { settingsStore.setBlockExplore(it) } })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val manager = getSystemService(AccessibilityManager::class.java)
        val component = ComponentName(this, ReelsGuardService::class.java)
        serviceEnabled = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        ).any { ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) == component }
    }

    private fun saveSetting(change: suspend () -> Unit) {
        lifecycleScope.launch {
            try {
                change()
                errorMessage = null
            } catch (_: IOException) { errorMessage = R.string.settings_error }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) { errorMessage = R.string.accessibility_error }
        catch (_: SecurityException) { errorMessage = R.string.accessibility_error }
    }

    private fun acceptAndOpenSettings() {
        lifecycleScope.launch {
            savingConsent = true
            try {
                settingsStore.acceptDisclosure()
                errorMessage = null
                openAccessibilitySettings()
            } catch (_: IOException) { errorMessage = R.string.settings_error }
            finally { savingConsent = false }
        }
    }
}

@Composable
private fun GuardScreen(
    serviceEnabled: Boolean,
    settings: GuardSettings?,
    errorMessage: Int?,
    savingConsent: Boolean,
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowToastChanged: (Boolean) -> Unit,
    onDebugChanged: (Boolean) -> Unit,
    onAutoSkipChanged: (Boolean) -> Unit,
    onStopAtEndChanged: (Boolean) -> Unit,
    onBlockedGroupsChanged: (Int) -> Unit,
    onBlockExploreChanged: (Boolean) -> Unit,
) {
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    Scaffold(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()).testTag("settings_scroll"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() })
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (serviceEnabled) R.string.status_enabled else R.string.status_disabled),
                        modifier = Modifier.testTag("service_status").semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(if (settings?.disclosureAccepted == true) R.string.consent_accepted else R.string.consent_needed),
                        modifier = Modifier.testTag("consent_status"))
                    Button(onClick = {
                        if (settings?.disclosureAccepted == true) onOpenSettings() else showDisclosure = true
                    }, enabled = settings != null && !savingConsent,
                        modifier = Modifier.testTag("accessibility_settings")) {
                        Text(stringResource(if (serviceEnabled) R.string.accessibility_settings else R.string.enable_service))
                    }
                    TextButton(onClick = { showDisclosure = true }, modifier = Modifier.testTag("disclosure")) {
                        Text(stringResource(R.string.review_disclosure))
                    }
                }
            }
            if (!serviceEnabled) Text(stringResource(R.string.restricted_settings_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.guard_settings), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            SettingSwitch("show_toast", R.string.show_toast, R.string.show_toast_description,
                settings?.showToast == true, settings != null, onShowToastChanged)
            SettingSwitch("auto_skip", R.string.auto_skip, R.string.auto_skip_description,
                settings?.autoSkipBlockedItems == true, settings != null, onAutoSkipChanged)
            SettingSwitch("stop_at_end", R.string.stop_at_end, R.string.stop_at_end_description,
                settings?.stopAtEndOfFollowedContent != false, settings != null, onStopAtEndChanged)
            val count = settings?.consecutiveBlockedGroups ?: 3
            Column {
                Text(stringResource(R.string.blocked_group_count, count), modifier = Modifier.testTag("blocked_group_count"))
                Text(stringResource(R.string.blocked_group_description), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = { onBlockedGroupsChanged(count - 1) },
                        enabled = settings?.stopAtEndOfFollowedContent == true && count > 1,
                        modifier = Modifier.testTag("fewer_groups")) { Text(stringResource(R.string.fewer_groups)) }
                    TextButton(onClick = { onBlockedGroupsChanged(count + 1) },
                        enabled = settings?.stopAtEndOfFollowedContent == true && count < Int.MAX_VALUE,
                        modifier = Modifier.testTag("more_groups")) { Text(stringResource(R.string.more_groups)) }
                }
            }
            SettingSwitch("block_explore", R.string.block_explore, R.string.block_explore_description,
                settings?.blockExplore != false, settings != null, onBlockExploreChanged)
            SettingSwitch("debug_mode", R.string.debug_diagnostics, R.string.debug_description,
                settings?.debugDiagnostics == true, settings != null, onDebugChanged)
            errorMessage?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
    if (showDisclosure) {
        AlertDialog(onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.disclosure_title)) },
            text = { Text(stringResource(R.string.disclosure_body), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = { showDisclosure = false; onEnable() }, enabled = settings != null && !savingConsent) {
                    Text(stringResource(R.string.agree_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text(stringResource(R.string.cancel)) }
            })
    }
}

@Composable
private fun SettingSwitch(tag: String, title: Int, description: Int, checked: Boolean, enabled: Boolean,
                          onChanged: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().testTag(tag)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChanged)
        .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(title))
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
