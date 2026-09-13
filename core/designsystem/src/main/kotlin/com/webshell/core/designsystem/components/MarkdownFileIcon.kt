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
                moveTo(5.6f, 3.4f)
                lineTo(18.4f, 3.4f)
                arcToRelative(2.1f, 2.1f, 0f, false, true, 2.1f, 2.1f)
                verticalLineTo(18.5f)
                arcToRelative(2.1f, 2.1f, 0f, false, true, -2.1f, 2.1f)
                horizontalLineTo(5.6f)
                arcToRelative(2.1f, 2.1f, 0f, false, true, -2.1f, -2.1f)
                verticalLineTo(5.5f)
                arcToRelative(2.1f, 2.1f, 0f, false, true, 2.1f, -2.1f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                // M
                moveTo(6.4f, 16.4f)
                verticalLineTo(7.7f)
                horizontalLineTo(8.2f)
                lineTo(10.35f, 10.85f)
                lineTo(12.5f, 7.7f)
                horizontalLineTo(14.3f)
                verticalLineTo(16.4f)
                horizontalLineTo(12.5f)
                verticalLineTo(11.15f)
                lineTo(10.35f, 14.15f)
                lineTo(8.2f, 11.15f)
                verticalLineTo(16.4f)
                close()
                // down-arrow
                moveTo(18.15f, 16.4f)
                lineTo(14.7f, 12.35f)
                horizontalLineTo(16.1f)
                verticalLineTo(7.7f)
                horizontalLineTo(18.15f)
                verticalLineTo(12.35f)
                horizontalLineTo(19.55f)
                close()
            }
        }.build().also { markdownFileIcon = it }
    }

private var markdownFileIcon: ImageVector? = null
