package com.webshell.core.data.metadata

import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

/** Offline capture of the public declaration observed on 2026-10-20. */
class DeepSeekIconRegressionTest {
    @Test fun svgDeclarationIsPreferredOverGuessedIcoEvenWhenRelTypeIsWrong() {
        val fetcher = SiteMetadataFetcher(OkHttpClient())
        val html = """<link rel="icon" type="image/x-icon" href="https://fe-static.deepseek.com/platform/favicon.svg">"""
        val icons = fetcher.rankDocumentIcons("https://platform.deepseek.com/", Jsoup.parse(html))
        assertEquals("https://fe-static.deepseek.com/platform/favicon.svg", icons.first())
        assertEquals("https://platform.deepseek.com/favicon.ico", icons.last())
    }
}
