package com.webshell.core.model

/** Snapshot of the selected document, never a live renderer or a temporary document URL. */
data class WebAppDraftSeed(val url: String, val title: String, val iconPath: String?, val token: String)
