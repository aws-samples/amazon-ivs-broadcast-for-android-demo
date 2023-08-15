package com.amazon.ivs.broadcast.ui.fragments

import androidx.lifecycle.ViewModel
import com.amazon.ivs.broadcast.cache.PreferenceProvider
import com.amazon.ivs.broadcast.cache.SecuredPreferenceProvider
import com.amazon.ivs.broadcast.common.*
import com.amazon.ivs.broadcast.common.broadcast.BroadcastManager
import com.amazon.ivs.broadcast.models.Orientation
import com.amazon.ivs.broadcast.models.Recommendation
import com.amazon.ivs.broadcast.models.ResolutionModel
import com.amazon.ivs.broadcast.models.ui.DeviceItem
import com.amazonaws.ivs.broadcast.BroadcastConfiguration
import com.amazonaws.ivs.broadcast.Device
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject
import kotlin.properties.Delegates

@HiltViewModel
class ConfigurationViewModel @Inject constructor(
    private val broadcastManager: BroadcastManager,
    private val securedPreferences: SecuredPreferenceProvider,
    private val preferences: PreferenceProvider,
) : ViewModel() {

    var isConfigurationChanged = true
    var developerMode = false
    var webViewUrl = AMAZON_IVS_URL
    var camerasList: List<DeviceItem>? = null
    var ingestServerUrl by Delegates.observable(securedPreferences.serverUrl) { _, _, newValue ->
        securedPreferences.serverUrl = newValue
    }
    var streamKey by Delegates.observable(securedPreferences.streamKey) { _, _, newValue ->
        securedPreferences.streamKey = newValue
    }
    var playbackUrl by Delegates.observable(securedPreferences.playbackUrl) { _, _, newValue ->
        securedPreferences.playbackUrl = newValue
    }
    var orientationId by Delegates.observable(preferences.orientation) { _, oldValue, newValue ->
        preferences.orientation = newValue
        isConfigurationChanged = oldValue != newValue
    }
    var isLandscape by Delegates.observable(false) { _, _, newValue ->
        resolution.isLandscape = newValue
    }
    var targetBitrate by Delegates.observable(preferences.targetBitrate) { _, oldValue, newValue ->
        val bitrate = newValue.takeIf { it in MIN_BPS .. MAX_BPS } ?: INITIAL_BPS
        preferences.targetBitrate = bitrate
        isConfigurationChanged = oldValue != bitrate
    }
    var minimumBitrate by Delegates.observable(preferences.customMinBitrate) { _, oldValue, newValue ->
        preferences.customMinBitrate = newValue
        isConfigurationChanged = oldValue != newValue
    }
    var maximumBitrate by Delegates.observable(preferences.customMaxBitrate) { _, oldValue, newValue ->
        preferences.customMaxBitrate = newValue
        isConfigurationChanged = oldValue != newValue
    }
    var framerate by Delegates.observable(preferences.customFrameRate) { _, oldValue, newValue ->
        preferences.customFrameRate = newValue
        isConfigurationChanged = oldValue != newValue
    }
    var autoAdjustBitrate by Delegates.observable(preferences.autoAdjustBitrate) { _, oldValue, newValue ->
        preferences.autoAdjustBitrate = newValue
        isConfigurationChanged = oldValue != newValue
    }
    var useCustomBitrateLimits by Delegates.observable(preferences.useCustomBitrateLimits) { _, oldValue, newValue ->
        preferences.useCustomBitrateLimits = newValue
        isConfigurationChanged = oldValue != newValue
    }
    var useCustomResolution by Delegates.observable(preferences.useCustomResolution) { _, oldValue, newValue ->
        preferences.useCustomResolution = newValue
        isConfigurationChanged = oldValue != newValue
    }
    var useCustomFramerate by Delegates.observable(preferences.useCustomFramerate) { _, oldValue, newValue ->
        preferences.useCustomFramerate = newValue
        isConfigurationChanged = oldValue != newValue
    }
    var recommendation by Delegates.observable(Recommendation()) { _, oldValue, newValue ->
        useCustomFramerate = false
        useCustomResolution = false
        autoAdjustBitrate = true
        isConfigurationChanged = oldValue != newValue
        targetBitrate = newValue.targetBitrate
        minimumBitrate = newValue.minBitrate
        maximumBitrate = newValue.maxBitrate
        resolution = ResolutionModel(newValue.width, newValue.height)
        framerate = newValue.frameRate
    }
    var resolution by Delegates.observable(
        ResolutionModel(
            preferences.width,
            preferences.height,
            orientation = preferences.orientation
        )
    ) { _, oldValue, newValue ->
        preferences.width = newValue.width
        preferences.height = newValue.height
        if (useCustomResolution) with(newValue) {
            orientationId = when {
                initialWidth > initialHeight -> Orientation.LANDSCAPE.id
                initialWidth < initialHeight -> Orientation.PORTRAIT.id
                else -> Orientation.SQUARE.id
            }
            newValue.orientation = orientationId
        }

        isConfigurationChanged = oldValue != newValue
    }
    var defaultCameraId by Delegates.observable(preferences.defaultCameraId) { _, _, newValue ->
        preferences.defaultCameraId = newValue
        camerasList?.onEach { it.isSelected = false }?.firstOrNull { it.deviceId == newValue }
            ?.apply { isSelected = true }
        camerasList?.forEach {
            Timber.d("${it.isSelected}")
        }
    }
    var defaultCameraPosition by Delegates.observable(preferences.defaultCameraPosition) { _, _, newValue ->
        preferences.defaultCameraPosition = newValue
    }

    val defaultDeviceItem get() = camerasList?.firstOrNull { it.isSelected }
    val newestConfiguration get() = BroadcastConfiguration().apply {
        val bitrate = targetBitrate.takeIf { it in MIN_BPS .. MAX_BPS } ?: INITIAL_BPS
        Timber.d("Initial bitrate: $bitrate")
        video.initialBitrate = bitrate
        video.maxBitrate = maximumBitrate
        video.minBitrate = minimumBitrate
        video.size = BroadcastConfiguration.Vec2(resolution.width, resolution.height)
        video.targetFramerate = framerate
        video.isUseAutoBitrate = autoAdjustBitrate
        audio.channels = 1
        mixer.slots = arrayOf(defaultSlot)
    }

    val defaultSlot: BroadcastConfiguration.Mixer.Slot get() = BroadcastConfiguration.Mixer.Slot.with { slot ->
        slot.name = SLOT_DEFAULT
        if (orientationId != Orientation.AUTO.id) {
            slot.aspect = BroadcastConfiguration.AspectMode.FILL
        }
        slot
    }

    val screenShareSlots: List<BroadcastConfiguration.Mixer.Slot> get() = listOf(
        BroadcastConfiguration.Mixer.Slot.with { slot ->
            val offset = 50f
            val width = resolution.width * 0.3f
            val height = resolution.height * 0.3f
            val posX = resolution.width - width - offset
            val posY = resolution.height - height - offset
            slot.name = SLOT_DEFAULT
            slot.setzIndex(10)
            slot.size = BroadcastConfiguration.Vec2(width, height)
            slot.position = BroadcastConfiguration.Vec2(posX, posY)
            if (orientationId != Orientation.AUTO.id) {
                slot.aspect = BroadcastConfiguration.AspectMode.FILL
            }
            slot.preferredAudioInput = Device.Descriptor.DeviceType.MICROPHONE
            slot.preferredVideoInput = Device.Descriptor.DeviceType.CAMERA
            slot
        },
        BroadcastConfiguration.Mixer.Slot.with { slot ->
            slot.name = SLOT_SCREEN_SHARE
            slot.aspect = BroadcastConfiguration.AspectMode.FILL
            slot.preferredAudioInput = Device.Descriptor.DeviceType.SYSTEM_AUDIO
            slot.preferredVideoInput = Device.Descriptor.DeviceType.SCREEN
            slot.gain = 0.3f
            slot
        }
    )

    init {
        broadcastManager.init(this)
        launch {
            broadcastManager.onDevicesListed.collect { devices ->
                camerasList = devices
            }
        }
    }

    fun resetDefaultCamera() {
        defaultCameraId = preferences.defaultCameraId
    }

    fun onConfigurationChanged(isLandscapeOrientation: Boolean) {
        Timber.d("Configuration changed: $isLandscapeOrientation")
        isLandscape = isLandscapeOrientation
        broadcastManager.reloadDevices()
    }
}
