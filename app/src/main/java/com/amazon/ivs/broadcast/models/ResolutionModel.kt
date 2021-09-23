package com.amazon.ivs.broadcast.models

data class ResolutionModel(
    var initialWidth: Float,
    var initialHeight: Float,
    var orientation: Int = Orientation.AUTO.id,
    var isLandscape: Boolean = false
) {
    private val isWidthLonger get() = initialWidth > initialHeight

    val shortestSide get() = if (isWidthLonger) initialHeight else initialWidth
    val widthAgainstHeightRatio get() = width / height
    val width: Float
        get() {
            return when (orientation) {
                Orientation.LANDSCAPE.id -> if (isWidthLonger) initialWidth else initialHeight
                Orientation.PORTRAIT.id -> if (isWidthLonger) initialHeight else initialWidth
                Orientation.SQUARE.id -> if (isWidthLonger) initialHeight else initialWidth
                else -> {
                    if (isWidthLonger) initialWidth else initialHeight
                }
            }
        }
    val height: Float
        get() {
            return when (orientation) {
                Orientation.LANDSCAPE.id -> if (isWidthLonger) initialHeight else initialWidth
                Orientation.PORTRAIT.id -> if (isWidthLonger) initialWidth else initialHeight
                Orientation.SQUARE.id -> if (isWidthLonger) initialHeight else initialWidth
                else -> {
                    if (isWidthLonger) initialHeight else initialWidth
                }
            }
        }
}
