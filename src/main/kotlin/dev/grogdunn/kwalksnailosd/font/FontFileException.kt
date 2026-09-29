package dev.grogdunn.kwalksnailosd.font

sealed class FontFileException(message: String) : IllegalArgumentException(message) {
    class InvalidFontFileWidth(val width: Int) : FontFileException("Invalid font file width: $width")
    class InvalidFontFileHeight(val height: Int) : FontFileException("Invalid font file height: $height")
}
