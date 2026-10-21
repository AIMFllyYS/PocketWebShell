package com.webshell.core.data.metadata

import java.io.File
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class IconDataUriTest {
    @Test fun percentEncodedSvgPreservesPlusAndUnicode() {
        val image = IconDataUri.decode("data:image/svg+xml,%3Csvg%3E中+%3C/svg%3E")!!
        assertEquals("svg", image.extension)
        assertEquals("<svg>中+</svg>", image.bytes.toString(Charsets.UTF_8))
    }

    @Test fun base64SvgIsSupportedWithoutTrustingAnExternalFileExtension() {
        val svg = "<svg xmlns='http://www.w3.org/2000/svg'/>"
        val uri = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.toByteArray())
        assertEquals(svg, IconDataUri.decode(uri)!!.bytes.toString(Charsets.UTF_8))
    }

    @Test fun malformedNonImageAndOversizedValuesAreRejected() {
        for (uri in listOf("data:text/html,<html>", "data:image/png,%GG", "data:image/png;base64,?",
            "data:image/svg+xml," + "x".repeat(IconDataUri.MAX_BYTES + 1))) assertNull(IconDataUri.decode(uri))
    }

    @Test fun singleHtmlImportsAnInlineSvgIntoItsOwnDirectory() {
        val dir = kotlin.io.path.createTempDirectory("inline-svg").toFile()
        try {
            File(dir, "index.html").writeText("""<link rel="icon" href="data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%3E%3C/svg%3E">""")
            val path = LocalHtmlIcon.existingPath(dir, "index.html")!!
            assertEquals(dir.canonicalFile, File(path).parentFile!!.canonicalFile)
            assertTrue(File(path).readText().startsWith("<svg"))
        } finally { dir.deleteRecursively() }
    }
}
