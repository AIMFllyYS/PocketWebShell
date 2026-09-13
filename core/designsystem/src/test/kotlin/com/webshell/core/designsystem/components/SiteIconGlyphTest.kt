package com.webshell.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SiteIconGlyphTest {
    @Test
    fun remoteSitesUseLetterGlyph() {
        assertEquals(SiteIconGlyph.Letter, siteIconGlyph(false, "https://github.com"))
        assertEquals(SiteIconGlyph.Letter, siteIconGlyph(false, "local://app-1/document.md"))
    }

    @Test
    fun localMarkdownUsesMarkdownGlyph() {
        assertEquals(
            SiteIconGlyph.Markdown,
            siteIconGlyph(true, "local://app-1/document.md"),
        )
    }

    @Test
    fun localHtmlUsesCodeGlyph() {
        assertEquals(
            SiteIconGlyph.LocalHtml,
            siteIconGlyph(true, "local://app-1/index.html"),
        )
    }
}
