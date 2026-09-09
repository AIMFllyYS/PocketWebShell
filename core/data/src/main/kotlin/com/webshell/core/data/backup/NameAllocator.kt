package com.webshell.core.data.backup

/**
 * 命名去重：期望名已被占用时依次尝试「name（1）」「name（2）」…（全角括号）。
 * 占用集合随分配实时更新，保证同一批导入内也不重名。
 */
internal class NameAllocator(taken: Collection<String> = emptyList()) {

    private val taken = taken.toMutableSet()

    fun isTaken(name: String): Boolean = name in taken

    fun allocate(desired: String): String {
        if (taken.add(desired)) return desired
        var suffix = 1
        while (true) {
            val candidate = "$desired（$suffix）"
            if (taken.add(candidate)) return candidate
            suffix++
        }
    }
}
