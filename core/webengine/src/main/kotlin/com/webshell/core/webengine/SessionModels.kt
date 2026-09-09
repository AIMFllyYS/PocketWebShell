package com.webshell.core.webengine

/** One lifecycle vocabulary shared by browser tabs, saved sites and direct URLs. */
enum class SessionSource { BROWSER_TAB, SAVED_SITE, DIRECT_URL, LOCAL_APP }

enum class SessionLifecycleState { CREATED, ACTIVE, BACKGROUND, RECOVERING, EVICTED, CLOSED }

data class BrowserSession(
    val sessionId: String,
    val source: SessionSource,
    val currentUrl: String? = null,
    val title: String = "",
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val thumbnailKey: String? = null,
    val lifecycleState: SessionLifecycleState = SessionLifecycleState.CREATED,
)
