package com.example.reelsblock

import android.accessibilityservice.AccessibilityService
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Reads redacted structural snapshots; only navigates away from blocked content. */
class ReelsGuardService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settings = GuardSettings()
    private val machine = GuardStateMachine()
    private val overlays by lazy { OverlayManager(this) { wall -> debug("Overlay touch consumed wall=$wall") } }
    private var pendingFeedDelta: Int? = null
    private var pendingFeedContentChanged = false
    private var pendingFeedScrolledToTop = false
    private val pendingPositions = mutableListOf<ReelsRules.PagerPosition>()
    private var processingScheduled = false
    private var stopping = false
    private var lastDiagnostic = ""
    private var lastToastAt: Long? = null
    private var previousBlocked = emptySet<Long>()
    private var dumpReceiverRegistered = false
    private val clearWindows = object : Runnable {
        override fun run() {
            try { overlays.clear() } catch (_: Exception) {
                if (!stopping) handler.postDelayed(this, 100L)
            }
        }
    }
    private val dumpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                if (intent?.action == DUMP_ACTION && BuildConfig.DEBUG &&
                    settings.debugDiagnostics && settings.disclosureAccepted
                ) {
                    dumpTree()
                }
            } catch (_: Exception) {
                debug("Node dump failed; no UI details logged")
            }
        }
    }

    // Never retain an AccessibilityEvent, node, or any Instagram text in delayed work.
    private val processEvent = Runnable {
        processingScheduled = false
        var phase = "snapshot"
        try {
            if (settings.disclosureAccepted) {
                val positions = pendingPositions.toList()
                pendingPositions.clear()
                val delta = pendingFeedDelta
                pendingFeedDelta = null
                val contentChanged = pendingFeedContentChanged
                pendingFeedContentChanged = false
                val scrolledToTop = pendingFeedScrolledToTop
                pendingFeedScrolledToTop = false
                val snapshot = readSnapshot()
                phase = "decision"
                val decision = machine.step(snapshot, viewport(), positions,
                    delta, settings.autoSkipBlockedItems, SystemClock.uptimeMillis(),
                    settings.stopAtEndOfFollowedContent, settings.consecutiveBlockedGroups, contentChanged, scrolledToTop,
                    settings.blockExplore)
                phase = "overlay"
                overlays.show(decision.covers, decision.wall, decision.exploreCover)
                if (decision.diagnostic.isNotEmpty() && decision.diagnostic != lastDiagnostic) {
                    debug("${decision.diagnostic}; overlays=${overlays.windowCount}")
                    lastDiagnostic = decision.diagnostic
                }
                phase = "navigation"
                val navigated = when (decision.action) {
                    GuardStateMachine.Action.HOME_OR_BACK -> {
                        if (clickHome()) true else {
                            val sent = backFromInstagram()
                            debug("Home unavailable: BACK success=$sent")
                            sent
                        }
                    }
                    GuardStateMachine.Action.BACK -> {
                        val sent = backFromInstagram()
                        debug("BACK dispatched success=$sent")
                        sent
                    }
                    GuardStateMachine.Action.NONE -> false
                    GuardStateMachine.Action.SCROLL_FEED -> scrollFeed(false).also { debug("Feed scroll success=$it") }
                    GuardStateMachine.Action.SCROLL_BACKWARD -> scrollFeed(true).also { debug("Wall SCROLL_BACKWARD fired=$it") }
                }
                updateToast(decision, navigated, snapshot != null)
            } else overlays.clear()
        } catch (error: Exception) {
            // An incomplete read must not discard DM identity or the skip cooldown.
            // Hide covers for this sample, then retry while the existing state is active.
            try { overlays.clear() } catch (_: Exception) { debug("Overlay cleanup will retry") }
            debug("Explore state=UNCERTAIN signals=incomplete-snapshot; Missing signals: complete structural snapshot; " +
                "phase=$phase error=${error.javaClass.simpleName}; allowing content")
        } finally {
            // Instagram-only events cannot report another app taking foreground.
            // Keep checking while the Home feed or a viewer latch is active.
            if ((settings.disclosureAccepted && machine.needsForegroundCheck) || overlays.hasWindows) scheduleProcessing()
        }
    }

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            try {
                GuardSettingsStore(applicationContext.guardDataStore).settings.collect {
                    settings = it
                    if (!it.debugDiagnostics) lastDiagnostic = ""
                    debug("Settings applied: toast=${it.showToast} debug=${it.debugDiagnostics} " +
                        "autoSkip=${it.autoSkipBlockedItems} stopAtEnd=${it.stopAtEndOfFollowedContent} " +
                        "N=${it.consecutiveBlockedGroups} blockExplore=${it.blockExplore}")
                    if (!it.disclosureAccepted) clearState() else scheduleProcessing()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                settings = GuardSettings()
                clearState()
                // No exception details: framework errors can include UI data.
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null || !ReelsRules.acceptsEvent(event.packageName, event.eventType)) return
            if (settings.disclosureAccepted) {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) pendingFeedContentChanged = true
                capturePagerEvent(event)
                scheduleProcessing()
            }
        } catch (_: Exception) {
            debug("Event handling failed; allowing content")
        }
    }

    @Suppress("DEPRECATION")
    private fun capturePagerEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val source = event.source ?: return
        try {
            val bounds = Rect()
            source.getBoundsInScreen(bounds)
            if (source.viewIdResourceName == ReelsRules.FEED_LIST &&
                bounds.toRuleBounds().intersects(viewport()) && event.fromIndex == 0 && event.scrollY == 0) {
                pendingFeedScrolledToTop = true
            }
            if (source.viewIdResourceName == ReelsRules.FEED_LIST &&
                bounds.toRuleBounds().intersects(viewport()) && Build.VERSION.SDK_INT >= 28 &&
                event.scrollDeltaY != 0) {
                pendingFeedDelta = (pendingFeedDelta ?: 0) + event.scrollDeltaY
            }
            val position = ReelsRules.pagerPosition(
                source.viewIdResourceName, bounds.toRuleBounds(), viewport(), event.eventType,
                event.fromIndex, event.toIndex, event.scrollY,
            )
            if (position != null) {
                if (pendingPositions.size == 32) pendingPositions.removeAt(0)
                pendingPositions.add(position)
                debug("Pager evidence type=${event.eventType} viewId=${ReelsRules.CLIPS_PAGER} " +
                    "bounds=[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}] " +
                    "fromIndex=${event.fromIndex} toIndex=${event.toIndex} " +
                    "scrollY=${event.scrollY} itemCount=${event.itemCount}")
            }
        } finally {
            source.recycle()
        }
    }

    private fun scheduleProcessing() {
        // Bound processing to one sample per 100 ms even during continuous video events.
        if (!stopping && !processingScheduled) {
            processingScheduled = true
            handler.postDelayed(processEvent, 100L)
        }
    }

    private fun viewport(): ReelsRules.Bounds = resources.displayMetrics.let {
        ReelsRules.Bounds(0, 0, it.widthPixels, it.heightPixels)
    }

    private fun Rect.toRuleBounds() = ReelsRules.Bounds(left, top, right, bottom)

    private fun updateToast(decision: GuardStateMachine.Decision, navigated: Boolean, instagram: Boolean) {
        if (!instagram) {
            overlays.hideToast()
            previousBlocked = emptySet()
            lastToastAt = null
            return
        }
        val blocked = decision.covers.map { it.groupId }.toSet() +
            listOfNotNull(decision.wall?.let { Long.MIN_VALUE }, decision.exploreCover?.let { Long.MIN_VALUE + 1 })
        val newlyBlocked = (blocked - previousBlocked).isNotEmpty() || navigated
        previousBlocked = blocked
        if (!settings.showToast) overlays.hideToast()
        val now = SystemClock.uptimeMillis()
        if (settings.showToast && newlyBlocked && (lastToastAt == null || now - lastToastAt!! >= 3000L)) {
            overlays.showToast()
            lastToastAt = now
            debug("Blocking toast shown")
        }
    }

    @Suppress("DEPRECATION")
    private fun readSnapshot(): ReelsRules.Node? {
        val root = rootInActiveWindow ?: return null
        try {
            if (!ReelsRules.INSTAGRAM_PACKAGE.contentEquals(root.packageName)) return null
            var count = 0
            fun read(node: AccessibilityNodeInfo, depth: Int): ReelsRules.Node {
                check(++count <= 2000 && depth <= 80)
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val children = (0 until node.childCount).map { index ->
                    val child = checkNotNull(node.getChild(index))
                    try { read(child, depth + 1) } finally { child.recycle() }
                }
                val descriptionLabels = ReelsRules.descriptionLabels(node.contentDescription)
                val labels = ReelsRules.Label.entries.filter {
                    ReelsRules.labelMatches(it, node.text, null)
                }.toSet() + descriptionLabels
                return ReelsRules.Node(node.viewIdResourceName, bounds.toRuleBounds(), node.isSelected,
                    children, labels, descriptionLabels, node.windowId to node.hashCode(),
                    node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD },
                    node.isFocused, node.isEditable, node.className?.toString())
            }
            return read(root, 0)
        } finally {
            root.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun clickHome(): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            if (!ReelsRules.INSTAGRAM_PACKAGE.contentEquals(root.packageName)) return false
            val nodes = root.findAccessibilityNodeInfosByViewId(ReelsRules.HOME_TAB)
            try {
                for (node in nodes) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    if (bounds.toRuleBounds().intersects(viewport()) && node.isClickable &&
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        debug("Home tab clicked")
                        return true
                    }
                }
                return false
            } finally {
                nodes.forEach { it.recycle() }
            }
        } finally {
            root.recycle()
        }
    }

    private fun clearState() {
        handler.removeCallbacks(processEvent)
        handler.removeCallbacks(clearWindows)
        processingScheduled = false
        pendingPositions.clear()
        pendingFeedDelta = null
        pendingFeedContentChanged = false
        pendingFeedScrolledToTop = false
        machine.reset()
        try { overlays.clear() } catch (_: Exception) {
            debug("Overlay cleanup will retry")
            if (!stopping) handler.postDelayed(clearWindows, 100L)
        }
        previousBlocked = emptySet()
        lastToastAt = null
        lastDiagnostic = ""
    }

    @Suppress("DEPRECATION")
    private fun scrollFeed(backward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            if (!ReelsRules.INSTAGRAM_PACKAGE.contentEquals(root.packageName)) return false
            val snapshot = readSnapshot() ?: return false
            if (!ReelsRules.isHome(snapshot, viewport())) return false
            val nodes = root.findAccessibilityNodeInfosByViewId(ReelsRules.FEED_LIST)
            try {
                val feed = nodes.singleOrNull { node ->
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    rect.toRuleBounds().intersects(viewport())
                } ?: return false
                val action = if (backward) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                if (backward && feed.actionList.none { it.id == action }) return false
                return feed.performAction(action)
            } finally {
                nodes.forEach { it.recycle() }
            }
        } finally {
            root.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun backFromInstagram(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            ReelsRules.INSTAGRAM_PACKAGE.contentEquals(root.packageName) &&
                performGlobalAction(GLOBAL_ACTION_BACK)
        } finally {
            root.recycle()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        stopping = false
        try {
            if (BuildConfig.DEBUG && !dumpReceiverRegistered) {
                // Shell holds DUMP; ordinary apps cannot trigger this exported receiver.
                ContextCompat.registerReceiver(
                    this, dumpReceiver, IntentFilter(DUMP_ACTION), Manifest.permission.DUMP,
                    handler, ContextCompat.RECEIVER_EXPORTED,
                )
                dumpReceiverRegistered = true
            }
        } catch (_: Exception) {
            debug("Dump receiver registration failed")
        }
    }

    @Suppress("DEPRECATION") // recycle is needed on API 26-32.
    private fun dumpTree() {
        val root = rootInActiveWindow ?: run {
            debug("Node dump skipped: no active root")
            return
        }
        if (!ReelsRules.INSTAGRAM_PACKAGE.contentEquals(root.packageName)) {
            root.recycle()
            debug("Node dump skipped: active window is not Instagram")
            return
        }
        val pending = ArrayDeque<Pair<AccessibilityNodeInfo, String>>()
        pending.addLast(root to "0")
        var visited = 0
        var unavailableChildren = 0
        var failedNodes = 0
        val lines = mutableListOf("Node dump begin")
        try {
            while (pending.isNotEmpty()) {
                val (node, path) = pending.removeLast()
                visited++
                try {
                    try {
                        val bounds = Rect()
                        node.getBoundsInScreen(bounds)
                        val description = node.contentDescription
                        lines.add(
                            "path=$path viewId=${node.viewIdResourceName} " +
                                "class=${node.className} selected=${node.isSelected} " +
                                "focused=${node.isFocused} editable=${node.isEditable} " +
                                "bounds=[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}] " +
                                ReelsRules.Label.entries.filter { ReelsRules.labelMatches(it, node.text, description) }
                                    .joinToString(" ") { "lbl=${it.name}=true" },
                        )
                    } catch (_: Exception) {
                        failedNodes++
                        lines.add("Node fields unavailable path=$path")
                    }
                    // Path records hierarchy indices, never visual order. Bounds record position.
                    // Walk every exposed child, regardless of visibility, bounds, or labels.
                    for (index in node.childCount - 1 downTo 0) {
                        try {
                            val child = node.getChild(index)
                            if (child != null) pending.addLast(child to "$path/$index")
                            else unavailableChildren++
                        } catch (_: Exception) {
                            unavailableChildren++
                        }
                    }
                } catch (_: Exception) {
                    failedNodes++
                    lines.add("Node children unavailable path=$path")
                } finally {
                    node.recycle()
                }
            }
        } finally {
            pending.forEach { (node, _) -> node.recycle() }
            lines.add("Node dump end visited=$visited unavailableChildren=$unavailableChildren failedNodes=$failedNodes")
            // Pace only redacted strings, never hold framework nodes or pause event handling.
            var offset = 0
            handler.post(object : Runnable {
                override fun run() {
                    val end = minOf(offset + 20, lines.size)
                    while (offset < end) debug(lines[offset++])
                    if (offset < lines.size) handler.postDelayed(this, 100L)
                }
            })
        }
    }

    override fun onInterrupt() {
        try {
            clearState()
        } catch (_: Exception) {
            debug("Event cleanup failed; allowing content")
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopping = true
        clearState()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopping = true
        try {
            if (dumpReceiverRegistered) unregisterReceiver(dumpReceiver)
        } catch (_: Exception) {
            debug("Dump receiver cleanup failed")
        } finally {
            clearState()
            overlays.destroy()
            scope.cancel()
            super.onDestroy()
        }
    }

    private fun debug(message: String) {
        if (settings.disclosureAccepted && settings.debugDiagnostics) Log.d("ReelsGuard", message)
    }

    private companion object {
        const val DUMP_ACTION = "com.example.reelsblock.DUMP"
    }
}
