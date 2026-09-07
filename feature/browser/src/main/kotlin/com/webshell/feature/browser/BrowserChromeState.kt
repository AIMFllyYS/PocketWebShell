package com.webshell.feature.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

enum class BottomChromeMode { Expanded, Orb, Edge }

enum class BrowserOverlay { Menu, Tabs, History, Bookmarks, CloseAll, ClearHistory }

/** Only transient chrome belongs here; tab data and WebView ownership never do. */
@Immutable
data class BrowserChromeState(
    val bottomMode: BottomChromeMode = BottomChromeMode.Expanded,
    val toolbarVisible: Boolean = true,
    val overlay: BrowserOverlay? = null,
    val visible: Boolean = false,
    val activeSessionId: String? = null,
    val autoCollapse: Boolean = true,
    val interactionBlocked: Boolean = false,
) {
    val canReadToCollapse: Boolean
        get() = visible && activeSessionId != null && autoCollapse &&
            !interactionBlocked && overlay == null && bottomMode == BottomChromeMode.Expanded
}

sealed interface BrowserChromeEvent {
    data object Reveal : BrowserChromeEvent
    data object Collapse : BrowserChromeEvent
    data object Park : BrowserChromeEvent
    data object HideToolbar : BrowserChromeEvent
    data class Reading(val sessionId: String) : BrowserChromeEvent
    data class ShowOverlay(val overlay: BrowserOverlay?) : BrowserChromeEvent
    data class Environment(
        val visible: Boolean,
        val activeSessionId: String?,
        val autoCollapse: Boolean,
        val interactionBlocked: Boolean,
    ) : BrowserChromeEvent
}

/** Pure reducer: no timers, no scroll offsets and no frame-driven state emission. */
fun reduceBrowserChrome(state: BrowserChromeState, event: BrowserChromeEvent): BrowserChromeState =
    when (event) {
        BrowserChromeEvent.Reveal -> state.copy(
            bottomMode = BottomChromeMode.Expanded, toolbarVisible = true,
        )
        BrowserChromeEvent.Collapse -> state.copy(bottomMode = BottomChromeMode.Orb, overlay = null)
        BrowserChromeEvent.Park -> if (state.bottomMode == BottomChromeMode.Orb) {
            state.copy(bottomMode = BottomChromeMode.Edge)
        } else state
        BrowserChromeEvent.HideToolbar -> state.copy(toolbarVisible = false, overlay = null)
        is BrowserChromeEvent.Reading -> if (
            state.canReadToCollapse && event.sessionId == state.activeSessionId
        ) state.copy(bottomMode = BottomChromeMode.Orb) else state
        is BrowserChromeEvent.ShowOverlay -> state.copy(overlay = event.overlay)
        is BrowserChromeEvent.Environment -> state.copy(
            visible = event.visible,
            activeSessionId = event.activeSessionId,
            autoCollapse = event.autoCollapse,
            interactionBlocked = event.interactionBlocked,
            // Empty/new-tab UI must never be stranded behind hidden controls.
            bottomMode = if (event.activeSessionId == null || (state.autoCollapse && !event.autoCollapse)) {
                BottomChromeMode.Expanded
            } else state.bottomMode,
            toolbarVisible = if (event.activeSessionId == null) true else state.toolbarVisible,
            overlay = if (event.visible) state.overlay else null,
        )
    }

/** Retain this above the main-tab host; it contains no Android objects. */
@Stable
class BrowserChromeController(initial: BrowserChromeState = BrowserChromeState()) {
    var state by mutableStateOf(initial)
        private set

    fun dispatch(event: BrowserChromeEvent) {
        state = reduceBrowserChrome(state, event)
    }

    companion object {
        /** Save presentation only. A recreated host supplies fresh session/visibility/IME state. */
        val Saver = listSaver<BrowserChromeController, Any>(
            save = { listOf(it.state.bottomMode.name, it.state.toolbarVisible) },
            restore = {
                BrowserChromeController(restoreChromePresentation(it.getOrNull(0) as? String, it.getOrNull(1) as? Boolean))
            },
        )
    }
}

@Composable
fun rememberBrowserChromeController(): BrowserChromeController =
    rememberSaveable(saver = BrowserChromeController.Saver) { BrowserChromeController() }

internal fun restoreChromePresentation(mode: String?, toolbarVisible: Boolean?): BrowserChromeState {
    val restoredMode = BottomChromeMode.entries.firstOrNull { it.name == mode }
    return BrowserChromeState(
        bottomMode = restoredMode ?: BottomChromeMode.Expanded,
        toolbarVisible = if (restoredMode == null) true else toolbarVisible ?: true,
    )
}
