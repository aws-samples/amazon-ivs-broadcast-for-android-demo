package com.amazon.ivs.broadcast.models

import com.amazonaws.ivs.broadcast.Device

enum class CameraDirection(val broadcast: Device.Descriptor.Position) {
    FRONT(Device.Descriptor.Position.FRONT),
    BACK(Device.Descriptor.Position.BACK)
}
