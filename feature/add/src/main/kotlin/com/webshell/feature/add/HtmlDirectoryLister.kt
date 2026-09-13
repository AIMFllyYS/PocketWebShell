package com.webshell.feature.add

import java.io.File

object HtmlDirectoryLister {
    const val MAX_ENTRIES = 500
    const val MAX_DIRECTORIES = 400

    sealed interface Outcome {
        data class Success(val entries: List<HtmlEntry>) : Outcome
        data object AccessDenied : Outcome
    }

    sealed interface HtmlEntry {
        val name: String
        val file: File

        data class Directory(override val name: String, override val file: File) : HtmlEntry
        data class HtmlFile(
            override val name: String,
            override val file: File,
            val sizeBytes: Long,
            val lastModified: Long,
        ) : HtmlEntry
    }

    fun isHtmlName(name: String): Boolean =
        name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true)

    fun list(dir: File, mustStayUnder: File? = null, allowAndroidData: Boolean = false): Outcome {
        val canonical = runCatching { dir.canonicalFile }.getOrNull() ?: return Outcome.AccessDenied
        if (!allowAndroidData && isAndroidProtected(canonical)) return Outcome.AccessDenied
        if (mustStayUnder != null) {
            val root = runCatching { mustStayUnder.canonicalFile }.getOrNull() ?: return Outcome.AccessDenied
            if (!HtmlImportRoots.isUnder(canonical, root)) return Outcome.AccessDenied
        }
        if (!canonical.exists()) return Outcome.Success(emptyList())
        if (!canonical.isDirectory) return Outcome.AccessDenied
        val children = canonical.listFiles() ?: return Outcome.AccessDenied
        var ghostHtml = false
        val directories = ArrayList<HtmlEntry.Directory>()
        val files = ArrayList<HtmlEntry.HtmlFile>()
        for (child in children) {
            if (child.name.startsWith('.')) continue
            if (!allowAndroidData && isBlockedChild(canonical, child)) continue
            when {
                child.isDirectory -> {
                    if (directories.size < MAX_DIRECTORIES) {
                        directories.add(HtmlEntry.Directory(child.name, child))
                    }
                }
                child.isFile && isHtmlName(child.name) -> {
                    if (files.size < MAX_ENTRIES) {
                        files.add(
                            HtmlEntry.HtmlFile(
                                name = child.name,
                                file = child,
                                sizeBytes = child.length(),
                                lastModified = child.lastModified(),
                            ),
                        )
                    }
                }
                !child.isDirectory && !child.isFile && isHtmlName(child.name) -> ghostHtml = true
            }
        }
        // Scoped storage often returns HTML names that fail isFile/stat. That is
        // not an empty folder — treat it as missing access.
        if (ghostHtml && files.isEmpty()) return Outcome.AccessDenied
        directories.sortBy { it.name.lowercase() }
        files.sortByDescending { it.lastModified }
        return Outcome.Success(directories + files)
    }

    private fun isBlockedChild(parent: File, child: File): Boolean {
        val blockedName = child.name.equals("data", ignoreCase = true) ||
            child.name.equals("obb", ignoreCase = true)
        return blockedName && parent.name.equals("Android", ignoreCase = true)
    }

    private fun isAndroidProtected(file: File): Boolean {
        val parts = file.path.split(File.separatorChar, '/', '\\')
        val androidIdx = parts.indexOfFirst { it.equals("Android", ignoreCase = true) }
        if (androidIdx < 0 || androidIdx >= parts.lastIndex) return false
        val next = parts[androidIdx + 1]
        return next.equals("data", ignoreCase = true) || next.equals("obb", ignoreCase = true)
    }
}
