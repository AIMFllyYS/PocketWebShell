package com.webshell.core.data.update

/**
 * PocketWebShell 的三段版本号。比较按数值、不进位：`0.1.9 < 0.1.10`。
 * 与 GitHub tag `v<versionName>` 对齐，不使用 Android `versionCode`。
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val micro: Int,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::micro)

    override fun toString(): String = "$major.$minor.$micro"

    companion object {
        fun parse(raw: String): AppVersion? {
            val value = raw.trim().removePrefix("v").removePrefix("V")
            val parts = value.split('.')
            if (parts.size != 3) return null
            val numbers = parts.map { part ->
                if (part.isEmpty() || part.any { !it.isDigit() }) return null
                if (part.length > 1 && part.startsWith('0')) return null
                part.toIntOrNull() ?: return null
            }
            return AppVersion(numbers[0], numbers[1], numbers[2])
        }

        /** [remote] 相对 [installed]：正数表示远程更新。任一无法解析则返回 null。 */
        fun compareNames(remote: String, installed: String): Int? {
            val left = parse(remote) ?: return null
            val right = parse(installed) ?: return null
            return left.compareTo(right)
        }
    }
}
