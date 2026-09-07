package com.webshell.core.webengine

import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import java.util.UUID

/**
 * Observes, never consumes, real touch events. No onScrollChanged listener or JS bridge.
 * Two bounded renderer queries per eligible gesture verify an actual document/inner-container
 * scroll and reject text selection. Page code can only influence a cosmetic collapse decision.
 * Cross-origin iframe internals are deliberately not inspected: their gestures keep chrome
 * expanded unless the outer document scrolls. The explicit collapse command remains available.
 */
internal class ReadingWebView(context: Context) : WebView(context) {
    var onReadingGesture: (() -> Unit)? = null
        set(value) {
            val wasObserving = field != null
            field = value
            if (value == null && wasObserving) cancelReadingGesture()
        }

    private val classifier = ReadingGestureClassifier(
        thresholdPx = 64f * resources.displayMetrics.density,
        touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
        longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong(),
    )
    private val probeKey = "__wsReading_${UUID.randomUUID().toString().replace("-", "")}"
    private var generation = 0
    private var selectingText = false
    private var probeActive = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (onReadingGesture != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    generation++
                    classifier.start(event.x, event.y, event.eventTime, event.pointerCount)
                    if (selectingText) classifier.cancel() else beginProbe(event.x, event.y)
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> cancelReadingGesture()
            }
        }
        val handled = super.dispatchTouchEvent(event)
        if (onReadingGesture != null && event.actionMasked == MotionEvent.ACTION_MOVE &&
            classifier.move(event.x, event.y, event.eventTime, event.pointerCount, selectingText)
        ) confirmReadingGesture()
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            // The bounded confirmation can finish just after UP; a new touch/navigation or
            // cancellation invalidates it via generation. This avoids dropping quick swipes.
            classifier.cancel()
            clearProbe()
        }
        return handled
    }

    private fun beginProbe(x: Float, y: Float) {
        if (width <= 0 || height <= 0) return
        probeActive = true
        val normalizedX = (x / width).coerceIn(0f, 1f)
        val normalizedY = (y / height).coerceIn(0f, 1f)
        evaluateJavascript(
            """(function(){
              try {
                var v=window.visualViewport;
                var x=$normalizedX*(v?v.width:innerWidth)+(v?v.offsetLeft:0);
                var y=$normalizedY*(v?v.height:innerHeight)+(v?v.offsetTop:0);
                var e=document.elementFromPoint(x,y), a=[];
                while(e){
                  if(e.scrollHeight>e.clientHeight+1)a.push([e,e.scrollTop]);
                  e=e.parentElement;
                }
                window['$probeKey']={nodes:a,top:window.scrollY};
              }catch(e){}
            })()""".trimIndent(), null,
        )
    }

    private fun confirmReadingGesture() {
        if (!probeActive) return
        probeActive = false
        val expectedGeneration = generation
        evaluateJavascript(
            """(function(){
              try {
                var p=window['$probeKey']; delete window['$probeKey'];
                if(!p || (window.getSelection() && !window.getSelection().isCollapsed))return false;
                if(window.scrollY>p.top+1)return true;
                return p.nodes.some(function(n){return n[0].isConnected && n[0].scrollTop>n[1]+1;});
              }catch(e){return false;}
            })()""".trimIndent(),
        ) { result ->
            if (generation == expectedGeneration && !selectingText && result == "true") {
                onReadingGesture?.invoke()
            }
        }
    }

    fun cancelReadingGesture() {
        generation++
        classifier.cancel()
        clearProbe()
    }

    private fun clearProbe() {
        if (!probeActive) return
        probeActive = false
        // Queued behind any confirmation, and only runs for a view that has been used.
        if (isAttachedToWindow) evaluateJavascript("delete window['$probeKey'];", null)
    }

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        cancelReadingGesture()
        val mode = super.startActionMode(object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean =
                callback.onCreateActionMode(mode, menu)
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
                callback.onPrepareActionMode(mode, menu)
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
                callback.onActionItemClicked(mode, item)
            override fun onDestroyActionMode(mode: ActionMode) {
                selectingText = false
                callback.onDestroyActionMode(mode)
            }
            override fun onGetContentRect(mode: ActionMode, view: android.view.View, outRect: android.graphics.Rect) {
                if (callback is ActionMode.Callback2) callback.onGetContentRect(mode, view, outRect)
                else super.onGetContentRect(mode, view, outRect)
            }
        }, type)
        selectingText = mode != null
        return mode
    }
}
