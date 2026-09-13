package com.webshell.core.designsystem.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Markdown file glyph for local `.md` apps and incoming Markdown tabs.
 *
 * Shape follows the public-domain Markdown Mark (Dustin Curtis): a rounded
 * document with the “M” and down-arrow. VS Code’s default Seti explorer icon
 * is the same mark in a single color (Seti UI, MIT). This is that one glyph,
 * not a second icon library.
 */
val MarkdownFileIcon: ImageVector
    get() {
        val cached = markdownFileIcon
        if (cached != null) return cached
        return ImageVector.Builder(
            name = "MarkdownFile",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.55f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.4f, 3.4f)
                lineTo(17.6f, 3.4f)
                arcToRelative(2.1f, 2.1f, 0f, false, true, 2.1f, 2.1f)
                verticalLineTo(18.5f)
                arcToRelative(2.1f, 2.1f, 0f, false, true, -2.1f, 2.1f)
                horizontalLineTo(6.4f)
                arcToRelative(2.1f, 2.1f, 0f, false, true, -2.1f, -2.1f)
                verticalLineTo(5.5f)
                arcToRelative(2.1f, 2.1f, 0f, false, true, 2.1f, -2.1f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                // M
                moveTo(6.9f, 16.4f)
                verticalLineTo(7.7f)
                horizontalLineTo(8.55f)
                lineTo(10.35f, 10.85f)
                lineTo(12.15f, 7.7f)
                horizontalLineTo(13.8f)
                verticalLineTo(16.4f)
                horizontalLineTo(12.2f)
                verticalLineTo(11.15f)
                lineTo(10.35f, 14.15f)
                lineTo(8.5f, 11.15f)
                verticalLineTo(16.4f)
                close()
                // down-arrow
                moveTo(17.55f, 16.4f)
                lineTo(14.35f, 12.35f)
                horizontalLineTo(15.7f)
                verticalLineTo(7.7f)
                horizontalLineTo(17.55f)
                verticalLineTo(12.35f)
                horizontalLineTo(18.9f)
                close()
            }
        }.build().also { markdownFileIcon = it }
    }

private var markdownFileIcon: ImageVector? = null
