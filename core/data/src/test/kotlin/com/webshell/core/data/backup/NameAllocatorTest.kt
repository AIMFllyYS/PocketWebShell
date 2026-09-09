package com.webshell.core.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameAllocatorTest {

    @Test
    fun `base name is used when free`() {
        val allocator = NameAllocator(taken = listOf("别的"))
        assertEquals("网站", allocator.allocate("网站"))
    }

    @Test
    fun `taken base name gets full-width suffix one`() {
        val allocator = NameAllocator(taken = listOf("网站"))
        assertEquals("网站（1）", allocator.allocate("网站"))
    }

    @Test
    fun `suffix increments when lower suffix taken`() {
        val allocator = NameAllocator(taken = listOf("网站", "网站（1）"))
        assertEquals("网站（2）", allocator.allocate("网站"))
    }

    @Test
    fun `allocated names become taken within the batch`() {
        val allocator = NameAllocator()
        assertEquals("网站", allocator.allocate("网站"))
        assertEquals("网站（1）", allocator.allocate("网站"))
        assertEquals("网站（2）", allocator.allocate("网站"))
    }

    @Test
    fun `unicode and emoji names round trip unchanged`() {
        val allocator = NameAllocator(taken = listOf("工具📁"))
        assertEquals("工具📁（1）", allocator.allocate("工具📁"))
        assertTrue(allocator.isTaken("工具📁"))
    }
}
