package com.amazon.ivs.broadcast.ui.fragments.main

import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.amazon.ivs.broadcast.cache.PreferenceProvider
import com.amazon.ivs.broadcast.common.broadcast.BroadcastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.properties.Delegates

@HiltViewModel
class MainViewModel @Inject constructor(
    private val broadcastManager: BroadcastManager,
    private val preferences: PreferenceProvider,
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

    var isOnboardingDone by Delegates.observable(preferences.isOnboardingDone) { _, _, isOnboardingDone ->
        preferences.isOnboardingDone = isOnboardingDone
    }

    fun switchCameraDirection() = broadcastManager.flipCameraDirection()

    fun toggleMute() = broadcastManager.toggleAudio()

    fun toggleCamera(bitmap: Bitmap) = broadcastManager.toggleVideo(bitmap)

    fun reloadDevices() = broadcastManager.reloadDevices()

    fun startScreenCapture(data: Intent?) = broadcastManager.startScreenCapture(data)

    fun stopScreenShare() = broadcastManager.stopScreenShare()

    fun resetSession() = broadcastManager.resetSession()

    fun createSession() = broadcastManager.createSession()

    fun startStream() = broadcastManager.startStream()

    fun reloadPreview() = broadcastManager.displayCameraOutput()
}
