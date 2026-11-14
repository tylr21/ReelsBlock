package com.example.reelsblock

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/** Owns every window and pooled view. Called only on the service's main looper. */
class OverlayManager(private val context: Context, private val onTouchConsumed: (Boolean) -> Unit = {}) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val active = mutableMapOf<Long, TextView>()
    private val pool = ArrayDeque<TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private var toast: TextView? = null
    private val expireToast = object : Runnable {
        override fun run() {
            try { hideToast() } catch (_: Exception) { handler.postDelayed(this, 100L) }
        }
    }
    val hasWindows: Boolean get() = active.isNotEmpty() || toast != null
    val windowCount: Int get() = active.size + if (toast != null) 1 else 0

    private data class CoverWindow(val id: Long, val rect: ReelsRules.Bounds, val text: Int)

    fun show(covers: List<GuardStateMachine.Cover>, wall: ReelsRules.Bounds? = null,
             explore: ReelsRules.Bounds? = null) {
        check(Looper.myLooper() == Looper.getMainLooper())
        // One reserved window belongs to the wall, independently of post tokens.
        val requested = covers.map { CoverWindow(it.groupId, it.rect,
            if (it.kind == ReelsRules.CoverKind.INLINE_REEL) R.string.reel_hidden else R.string.non_followed_hidden)
        } + listOfNotNull(wall?.let { CoverWindow(Long.MIN_VALUE, it, R.string.caught_up_wall) },
            explore?.let { CoverWindow(Long.MIN_VALUE + 1, it, R.string.explore_disabled) })
        val ids = requested.map { it.id }.toSet()
        active.keys.filter { it !in ids }.forEach(::remove)
        for (cover in requested) {
            val existing = active[cover.id]
            val view = existing ?: pool.removeFirstOrNull() ?: TextView(context).apply {
                setBackgroundColor(Color.BLACK)
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(24, 24, 24, 24)
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                setOnTouchListener { touched, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) onTouchConsumed(touched.tag == Long.MIN_VALUE)
                    true
                }
            }
            view.tag = cover.id
            view.setText(cover.text)
            val b = cover.rect
            val params = WindowManager.LayoutParams(
                b.right - b.left, b.bottom - b.top,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE,
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = b.left
                y = b.top
                if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            }
            if (existing == null) {
                active[cover.id] = view
                windows.addView(view, params)
            } else if (!sameRect(view.layoutParams as WindowManager.LayoutParams, params)) {
                windows.updateViewLayout(view, params)
            }
        }
    }

    private fun sameRect(a: WindowManager.LayoutParams, b: WindowManager.LayoutParams) =
        a.x == b.x && a.y == b.y && a.width == b.width && a.height == b.height

    /** System toasts sit behind accessibility covers on the test phone. */
    fun showToast() {
        check(Looper.myLooper() == Looper.getMainLooper())
        hideToast()
        val density = context.resources.displayMetrics.density
        val view = TextView(context).apply {
            setText(R.string.blocked_toast)
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding((24 * density).toInt(), (12 * density).toInt(),
                (24 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.DKGRAY)
                cornerRadius = 24 * density
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (100 * density).toInt()
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }
        toast = view
        windows.addView(view, params)
        handler.postDelayed(expireToast, 2000L)
    }

    fun hideToast() {
        check(Looper.myLooper() == Looper.getMainLooper())
        handler.removeCallbacks(expireToast)
        val view = toast ?: return
        try { windows.removeViewImmediate(view) } catch (_: IllegalArgumentException) { }
        toast = null
    }

    private fun remove(id: Long) {
        val view = active.getValue(id)
        try {
            windows.removeViewImmediate(view)
        } catch (_: IllegalArgumentException) {
            // Android already detached the window, or addView failed.
        }
        active.remove(id)
        pool.addLast(view)
    }

    fun clear() {
        check(Looper.myLooper() == Looper.getMainLooper())
        hideToast()
        active.keys.toList().forEach(::remove)
    }

    fun destroy() {
        clear()
        pool.clear()
    }
}
