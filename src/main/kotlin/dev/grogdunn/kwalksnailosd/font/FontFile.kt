package dev.grogdunn.kwalksnailosd.font

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Path

data class FontFile(
    val filePath: Path,
    val characterCount: Int,
    val characterSize: CharacterSize,
    val fontType: FontType,
    val characters: List<BufferedImage>
) {
    fun getCharacter(index: Int, targetSize: CharacterSize = characterSize): BufferedImage? {
        val originalImage = characters.getOrNull(index) ?: return null
        if (targetSize.width != characterSize.width || targetSize.height != characterSize.height) {
            return resizeImage(originalImage, targetSize.width, targetSize.height)
        }
        return originalImage
    }

    private fun resizeImage(original: BufferedImage, targetWidth: Int, targetHeight: Int): BufferedImage {
        val resized = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = resized.createGraphics()
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null)
        } finally {
            g2d.dispose()
        }
        return resized
    }
}
