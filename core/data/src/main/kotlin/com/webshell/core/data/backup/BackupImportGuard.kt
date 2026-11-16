package com.webshell.core.data.backup

import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * 导入前的纯校验，没有 Android 依赖，JVM 单测可以直接调用。
 *
 * 路径规则作用在 zip 条目名上：先拒绝绝对路径、`..` / `.` 路径段、空段、反斜杠、NUL
 * 和百分号编码的穿越，再要求 canonical 目的地严格落在目标目录里面。
 * 字节上限只限制「选中的文件拷进缓存」，避免清单校验之前把缓存写满。
 */
internal object BackupImportGuard {

    /**
     * 512 MiB。完整备份会带上 WebView profile，体积可以比较大；
     * 这个上限是缓存拷贝的本地拒绝服务限制，不是备份格式或导出内容的变更。
     */
    const val MAX_IMPORT_BYTES: Long = 512L * 1024L * 1024L

    const val OVERSIZE_MESSAGE = "备份文件超过 512 MB 上限，已取消导入"

    const val UNSAFE_PATH_MESSAGE = "备份包含不安全的路径，已取消导入"

    private const val MAX_PERCENT_DECODES = 4

    /**
     * 把 [entryNames]（调用方已筛成这个 prefix 下的文件条目）变成相对路径。
     * 任一名字不安全就抛 [IOException]，不返回空列表，避免调用方把它当成「这棵树不存在」。
     */
    fun relativeNames(prefix: String, entryNames: List<String>): List<String> {
        if (prefix.isEmpty()) throw IOException(UNSAFE_PATH_MESSAGE)
        val relatives = ArrayList<String>(entryNames.size)
        for (name in entryNames) {
            if (!name.startsWith(prefix) || !isSafeZipEntryName(name)) {
                throw IOException(UNSAFE_PATH_MESSAGE)
            }
            val relative = name.removePrefix(prefix)
            if (!isSafeZipEntryName(relative)) throw IOException(UNSAFE_PATH_MESSAGE)
            relatives.add(relative)
        }
        return relatives
    }

    /**
     * 校验这一棵树的全部相对路径并返回 canonical 目的地。
     * 只计算路径，不创建目录、不写文件；任一名字不合法或落在 [root] 之外则整表失败。
     */
    fun resolveTree(root: File, relativeNames: List<String>): List<File> {
        val rootCanonical = try {
            root.canonicalFile
        } catch (_: IOException) {
            throw IOException(UNSAFE_PATH_MESSAGE)
        }
        val resolved = ArrayList<File>(relativeNames.size)
        for (relative in relativeNames) {
            if (!isSafeZipEntryName(relative)) throw IOException(UNSAFE_PATH_MESSAGE)
            val destination = try {
                File(rootCanonical, relative).canonicalFile
            } catch (_: IOException) {
                throw IOException(UNSAFE_PATH_MESSAGE)
            }
            if (!isStrictlyInside(destination, rootCanonical)) throw IOException(UNSAFE_PATH_MESSAGE)
            resolved.add(destination)
        }
        return resolved
    }

    /**
     * `.` 与 `..` 按路径段判断，因此 `index.html` 和 `notes..txt` 仍然合法。
     * `%HH` 会重复解码后再检查；解码后仍在变化则直接拒绝。`+` 不当作空格。
     */
    fun isSafeZipEntryName(name: String): Boolean {
        var current = name
        repeat(MAX_PERCENT_DECODES) {
            if (!singleFormSafe(current)) return false
            val decoded = percentDecode(current)
            if (decoded == current) return true
            current = decoded
        }
        return false
    }

    /**
     * 读到超过 [maxBytes] 就停止，删除 [dest] 并抛固定中文错误（不含 URI 或条目路径）。
     * 形状与外部文件的有界拷贝一致：先累计本次读取，超限的那一块不写入。
     * 空输入保留空文件并返回 0，让调用方继续按「不是备份」拒绝，而不是报超限。
     */
    fun copyBounded(input: InputStream, dest: File, maxBytes: Long = MAX_IMPORT_BYTES): Long {
        var copied = 0L
        var overflow = false
        try {
            input.use { source ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > maxBytes) {
                            overflow = true
                            break
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } catch (failure: Exception) {
            dest.delete()
            throw failure
        }
        if (overflow) {
            dest.delete()
            throw IOException(OVERSIZE_MESSAGE)
        }
        return copied
    }

    private fun singleFormSafe(name: String): Boolean {
        if (name.isEmpty()) return false
        if (name.any { it == '\\' || it == '\u0000' || it.isISOControl() }) return false
        if (isAbsoluteName(name)) return false
        val segments = name.split('/')
        for (segment in segments) {
            if (segment.isEmpty() || segment == "." || segment == "..") return false
        }
        return true
    }

    private fun isAbsoluteName(name: String): Boolean {
        if (name[0] == '/' || name[0] == '\\') return true
        return name.length >= 2 && name[1] == ':' && name[0].isAsciiLetter()
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

    /** 只解码合法的 `%HH`，避免 [java.net.URLDecoder] 把 `+` 换成空格。 */
    private fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current == '%' && index + 2 < value.length) {
                val high = hexNibble(value[index + 1])
                val low = hexNibble(value[index + 2])
                if (high >= 0 && low >= 0) {
                    out.append((high shl 4 or low).toChar())
                    index += 3
                    continue
                }
            }
            out.append(current)
            index++
        }
        return out.toString()
    }

    private fun hexNibble(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> -1
    }

    private fun isStrictlyInside(child: File, root: File): Boolean {
        val childPath = child.toPath()
        val rootPath = root.toPath()
        return childPath != rootPath && childPath.startsWith(rootPath)
    }
}
