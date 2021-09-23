package com.amazon.ivs.broadcast.models

import com.amazon.ivs.broadcast.common.INITIAL_BPS
import com.amazon.ivs.broadcast.common.INITIAL_FRAMERATE
import com.amazon.ivs.broadcast.common.INITIAL_HEIGHT
import com.amazon.ivs.broadcast.common.INITIAL_WIDTH

data class Recommendation(
    var width: Float = INITIAL_WIDTH,
    var height: Float = INITIAL_HEIGHT,
    var frameRate: Int = INITIAL_FRAMERATE,
    val minBitrate: Int = INITIAL_BPS,
    var targetBitrate: Int = INITIAL_BPS,
    val maxBitrate: Int = INITIAL_BPS
)
