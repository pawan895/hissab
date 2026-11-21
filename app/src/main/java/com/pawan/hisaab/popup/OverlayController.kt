package com.pawan.hisaab.popup

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pawan.hisaab.ui.theme.HisaabTheme

data class PopupRequest(val txnId: Long, val autoCategorized: Boolean, val preview: Boolean = false)

/** Lifecycle + SavedState owner so a ComposeView can live in a WindowManager overlay. */
private class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

/**
 * Shows the Truecaller-style card on top of whatever app is open. One card at a time;
 * payments that arrive while a card is showing are queued.
 */
object OverlayController {
    private class Showing(val view: ComposeView, val owner: OverlayLifecycleOwner, val params: WindowManager.LayoutParams)

    private val queue = ArrayDeque<PopupRequest>()
    private var showing: Showing? = null

    @MainThread
    fun enqueue(context: Context, request: PopupRequest): Boolean {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return false
        if (showing != null) {
            queue.addLast(request)
            return true
        }
        return display(app, request)
    }

    private fun display(context: Context, request: PopupRequest): Boolean {
        val wm = context.getSystemService(WindowManager::class.java)
        val owner = OverlayLifecycleOwner()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable by default so the app underneath keeps the keyboard and back button.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Bottom: within thumb reach, and clear of the SMS app's own heads-up notification at the top.
            gravity = Gravity.BOTTOM
            windowAnimations = android.R.style.Animation_Toast
        }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                HisaabTheme {
                    PopupCard(
                        request = request,
                        onDismiss = { dismiss(context) },
                        onNeedKeyboard = { setFocusable(context, it) },
                    )
                }
            }
        }
        owner.start()
        return try {
            wm.addView(view, params)
            showing = Showing(view, owner, params)
            true
        } catch (e: Exception) {
            owner.stop()
            false
        }
    }

    @MainThread
    fun dismiss(context: Context) {
        val s = showing ?: return
        showing = null
        runCatching { context.getSystemService(WindowManager::class.java).removeView(s.view) }
        s.owner.stop()
        queue.removeFirstOrNull()?.let { display(context, it) }
    }

    private fun setFocusable(context: Context, focusable: Boolean) {
        val s = showing ?: return
        s.params.flags = if (focusable) {
            s.params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            s.params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { context.getSystemService(WindowManager::class.java).updateViewLayout(s.view, s.params) }
    }
}
