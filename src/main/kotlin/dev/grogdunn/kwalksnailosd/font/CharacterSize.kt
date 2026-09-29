package dev.grogdunn.kwalksnailosd.font

enum class CharacterSize(
    val width: Int,
    val height: Int,
    val resolutionLabel: String
) {
    RACE(18, 27, "540p"),
    SMALL(24, 36, "720p"),
    LARGE(36, 54, "1080p"),
    XLARGE(48, 72, "2.7K"),
    ULTRA(72, 108, "4K");

    override fun toString(): String = resolutionLabel
}
