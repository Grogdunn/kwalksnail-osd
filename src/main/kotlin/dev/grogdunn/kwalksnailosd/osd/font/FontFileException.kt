package dev.grogdunn.kwalksnailosd.osd.font

import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.error_font_height
import dev.grogdunn.kwalksnailosd.generated.resources.error_font_width
import dev.grogdunn.kwalksnailosd.uiText

sealed class FontFileException(message: String) : IllegalArgumentException(message) {
    class InvalidFontFileWidth(val width: Int) : FontFileException(uiText(Res.string.error_font_width, width))
    class InvalidFontFileHeight(val height: Int) : FontFileException(uiText(Res.string.error_font_height, height))
}
