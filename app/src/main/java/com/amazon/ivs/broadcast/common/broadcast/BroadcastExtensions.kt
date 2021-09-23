package com.amazon.ivs.broadcast.common.broadcast

import android.content.Context
import com.amazonaws.ivs.broadcast.BroadcastSession.listAvailableDevices
import com.amazonaws.ivs.broadcast.Device

fun Context.listAvailableCameras(): List<Device.Descriptor> =
    try {
       listAvailableDevices(this).filter { it.type == Device.Descriptor.DeviceType.CAMERA }
    } catch (e: Exception) {
        listOf()
    }

fun Context.getCamera(position: Device.Descriptor.Position) = listAvailableCameras()
    .firstOrNull { it.position == position }

fun Device.Descriptor.isExternal(): Boolean =
    position == Device.Descriptor.Position.USB || position == Device.Descriptor.Position.BLUETOOTH || position == Device.Descriptor.Position.AUX
