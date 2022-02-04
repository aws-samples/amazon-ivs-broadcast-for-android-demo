package com.amazon.ivs.broadcast.ui.fragments.main

import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.amazon.ivs.broadcast.common.broadcast.*
import com.amazon.ivs.broadcast.common.launch
import com.amazon.ivs.broadcast.ui.fragments.ConfigurationViewModel
import timber.log.Timber

class MainViewModel(
    private val configurationViewModel: ConfigurationViewModel,
    private val broadcastManager: BroadcastManager
) : ViewModel() {

    val isCameraOff get() = broadcastManager.isVideoMuted
    val isStreamMuted get() = broadcastManager.isAudioMuted
    val isScreenShareEnabled get() = broadcastManager.isScreenShareEnabled
    val isStreamOnline get() = broadcastManager.isStreamOnline
    val currentConfiguration get() = broadcastManager.currentConfiguration

    val onScreenShareEnabled = broadcastManager.onScreenShareEnabled
    val onPreviewUpdated = broadcastManager.onPreviewUpdated
    val onAudioMuted = broadcastManager.onAudioMuted
    val onVideoMuted = broadcastManager.onVideoMuted
    val onError = broadcastManager.onError
    val onBroadcastState = broadcastManager.onBroadcastState
    val onStreamDataChanged = broadcastManager.onStreamDataChanged

    init {
        launch {
            broadcastManager.onDevicesListed.collect { devices ->
                configurationViewModel.camerasList = devices
            }
        }
    }

    fun switchCameraDirection() = broadcastManager.flipCameraDirection()

    fun toggleMute() = broadcastManager.toggleAudio()

    fun toggleCamera(bitmap: Bitmap) = broadcastManager.toggleVideo(bitmap)

    fun onConfigurationChanged(isLandscape: Boolean) {
        Timber.d("Configuration changed")
        configurationViewModel.isLandscape = isLandscape
        reloadDevices()
    }

    fun reloadDevices() = broadcastManager.reloadDevices()

    fun startScreenCapture(data: Intent?) = broadcastManager.startScreenCapture(data)

    fun stopScreenShare() = broadcastManager.stopScreenShare()

    fun resetSession() = broadcastManager.resetSession()

    fun createSession() = broadcastManager.createSession()

    fun startStream() = broadcastManager.startStream()
}
