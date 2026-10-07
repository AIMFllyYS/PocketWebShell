package com.webshell.core.model

/** Global webpage preference; native controls retain their own safe areas. */
enum class WebTopInsetMode(val storedValue: String) {
    AUTO("auto"),
    AVOID("avoid"),
    EDGE_TO_EDGE("edge_to_edge");

    companion object {
        fun fromStored(value: String?): WebTopInsetMode =
            entries.firstOrNull { it.storedValue == value } ?: AUTO
    }
}
