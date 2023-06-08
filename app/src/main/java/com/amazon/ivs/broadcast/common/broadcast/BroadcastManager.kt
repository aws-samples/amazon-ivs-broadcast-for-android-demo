package com.amazon.ivs.broadcast.common.broadcast

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.view.ViewGroup
import android.widget.LinearLayout
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.common.BYTES_TO_MEGABYTES_FACTOR
import com.amazon.ivs.broadcast.common.SLOT_DEFAULT
import com.amazon.ivs.broadcast.common.asString
import com.amazon.ivs.broadcast.common.getSessionUsedBytes
import com.amazon.ivs.broadcast.common.launchMain
import com.amazon.ivs.broadcast.common.slotNames
import com.amazon.ivs.broadcast.models.ui.DeviceItem
import com.amazon.ivs.broadcast.models.ui.StreamTopBarModel
import com.amazon.ivs.broadcast.ui.activities.NotificationActivity
import com.amazon.ivs.broadcast.ui.fragments.ConfigurationViewModel
import com.amazonaws.ivs.broadcast.BroadcastConfiguration
import com.amazonaws.ivs.broadcast.BroadcastException
import com.amazonaws.ivs.broadcast.BroadcastSession
import com.amazonaws.ivs.broadcast.Device
import com.amazonaws.ivs.broadcast.ErrorType
import com.amazonaws.ivs.broadcast.ImageDevice
import com.amazonaws.ivs.broadcast.SurfaceSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

private const val NOTIFICATION_CHANNEL_ID = "notificationId"
private const val NOTIFICATION_CHANNEL_NAME = "notificationName"

enum class BroadcastError(val error: Int) {
    FATAL(R.string.error_fatal),
    DEVICE_DISCONNECTED(R.string.error_device_disconnected),
    INVALID_CREDENTIALS(R.string.error_invalid_credentials)
}

enum class BroadcastState {
    BROADCAST_STARTING,
    BROADCAST_STARTED,
    BROADCAST_ENDED,
}

class BroadcastManager(private val context: Context) {

    private var isBackCamera = true
    private val cameraDirection get() = if (isBackCamera) Device.Descriptor.Position.BACK else Device.Descriptor.Position.FRONT

    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable = object : Runnable {
        override fun run() {
            try {
                val usedMegaBytes = getSessionUsedBytes(startBytes) / BYTES_TO_MEGABYTES_FACTOR
                timeInSeconds += 1
                _onStreamDataChanged.update {
                    StreamTopBarModel(
                        seconds = timeInSeconds,
                        usedMegaBytes = usedMegaBytes
                    )
                }
            } finally {
                timerHandler.postDelayed(this, 1000)
            }
        }
    }
    private var startBytes = 0f
    private var timeInSeconds = 0
    private var currentState = BroadcastState.BROADCAST_ENDED

    private var _onError = Channel<BroadcastError>()
    private var _onBroadcastState = MutableStateFlow(currentState)
    private var _onStreamDataChanged = MutableStateFlow<StreamTopBarModel?>(null)
    private var _onPreviewUpdated = MutableStateFlow<TextureView?>(null)
    private var _onAudioMuted = MutableStateFlow(false)
    private var _onVideoMuted = MutableStateFlow(false)
    private var _onScreenShareEnabled = Channel<Boolean>()
    private var _onDevicesListed = MutableStateFlow<List<DeviceItem>>(emptyList())

    private lateinit var configuration: ConfigurationViewModel
    private var session: BroadcastSession? = null
    private var cameraDevice: Device? = null
    private var microphoneDevice: Device.Descriptor? = null
    private var cameraOffDevice: SurfaceSource? = null
    private var cameraOffBitmap: Bitmap? = null
    private var screenDevices = mutableListOf<Device>()

    private var broadcastListener = object : BroadcastSession.Listener() {
        override fun onStateChanged(state: BroadcastSession.State) {
            Timber.d("Broadcast state changed: $state")
            when (state) {
                BroadcastSession.State.INVALID,
                BroadcastSession.State.DISCONNECTED,
                BroadcastSession.State.ERROR -> {
                    currentState = BroadcastState.BROADCAST_ENDED
                    _onBroadcastState.update { currentState }
                    resetTimer()
                }
                BroadcastSession.State.CONNECTING -> {
                    currentState = BroadcastState.BROADCAST_STARTING
                    _onBroadcastState.update { currentState }
                }
                BroadcastSession.State.CONNECTED -> {
                    currentState = BroadcastState.BROADCAST_STARTED
                    _onBroadcastState.update { currentState }
                    timerRunnable.run()
                }
            }
        }

        override fun onDeviceRemoved(descriptor: Device.Descriptor) {
            super.onDeviceRemoved(descriptor)
            if (descriptor.deviceId == microphoneDevice?.deviceId && descriptor.isExternal() && descriptor.isValid) {
                Timber.d("Microphone removed: ${descriptor.deviceId}, ${descriptor.position}")
                microphoneDevice = null
                session?.detachDevice(descriptor)
            }
            if (descriptor.deviceId == cameraDevice?.descriptor?.deviceId && descriptor.isExternal() && descriptor.isValid) {
                Timber.d("Camera removed: ${descriptor.deviceId}, ${descriptor.position}")
                cameraDevice = null
                session?.detachDevice(descriptor)
            }
        }

        override fun onDeviceAdded(descriptor: Device.Descriptor) {
            super.onDeviceAdded(descriptor)
            if (descriptor.isExternal() && descriptor.type == Device.Descriptor.DeviceType.MICROPHONE) {
                Timber.d("Microphone added: ${descriptor.deviceId}, ${descriptor.position}, ${descriptor.type}")
                microphoneDevice = descriptor
            }
        }

        override fun onError(error: BroadcastException) {
            Timber.d("Broadcast error: $error")
            if (error.error == ErrorType.ERROR_DEVICE_DISCONNECTED && error.source == microphoneDevice?.urn) {
                microphoneDevice?.let {
                    try {
                        session?.exchangeDevices(it, it) { microphone ->
                            Timber.d("Device with id ${microphoneDevice?.deviceId} reattached")
                            microphoneDevice = microphone.descriptor
                        }
                    } catch (e: BroadcastException) {
                        Timber.d(e, "Microphone exchange exception")
                        _onError.trySend(BroadcastError.DEVICE_DISCONNECTED)
                    }
                }
            } else if (error.error == ErrorType.ERROR_DEVICE_DISCONNECTED && microphoneDevice == null) {
                _onError.trySend(BroadcastError.DEVICE_DISCONNECTED)
            } else if (error.isFatal) {
                error.printStackTrace()
                _onError.trySend(BroadcastError.FATAL)
            }
        }
    }

    lateinit var currentConfiguration: BroadcastConfiguration
        private set
    var isScreenShareEnabled = false
        private set
    var isVideoMuted = false
        private set
    var isAudioMuted = false
        private set
    val isStreamOnline get() = currentState == BroadcastState.BROADCAST_STARTED

    val onError = _onError.receiveAsFlow()
    val onBroadcastState = _onBroadcastState.asStateFlow()
    val onPreviewUpdated = _onPreviewUpdated.asStateFlow()
    val onAudioMuted = _onAudioMuted.asStateFlow()
    val onVideoMuted = _onVideoMuted.asStateFlow()
    val onScreenShareEnabled = _onScreenShareEnabled.receiveAsFlow()
    val onStreamDataChanged = _onStreamDataChanged.asStateFlow()
    val onDevicesListed = _onDevicesListed.asStateFlow()

    fun init(configuration: ConfigurationViewModel) {
        this.configuration = configuration
    }

    fun createSession() {
        startBytes = (TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()).toFloat()
        currentConfiguration = configuration.newestConfiguration
        Timber.d("Creating session with configuration: ${currentConfiguration.asString()}")
        session = BroadcastSession(context, broadcastListener, currentConfiguration, null)
        attachInitialDevices()
        Timber.d("Session created")
    }

    fun resetSession() {
        var releasingSession = false
        session?.run {
            releasingSession = true
            Timber.d("Releasing session")
            cameraDevice?.run { detachDevice(this) }
            microphoneDevice?.run { detachDevice(this) }
            cameraOffDevice?.run { detachDevice(this) }
            screenDevices.forEach { device ->
                detachDevice(device)
            }
            stopSystemCapture()
            stop()
            release()
        }
        _onBroadcastState.update { BroadcastState.BROADCAST_ENDED }
        _onPreviewUpdated.update { null }
        cameraDevice = null
        microphoneDevice = null
        cameraOffDevice = null
        cameraOffBitmap = null
        session = null
        screenDevices.clear()
        if (releasingSession) {
            Timber.d("Session released")
        }
    }

    fun startStream() {
        Timber.d("Starting stream: ${configuration.ingestServerUrl}, ${configuration.streamKey}")
        try {
            session?.start(configuration.ingestServerUrl, configuration.streamKey)
        } catch (e: BroadcastException) {
            Timber.d("Stream error: ${e.detail}, ${e.code}")
            _onError.trySend(BroadcastError.INVALID_CREDENTIALS)
        }
    }

    fun flipCameraDirection() {
        val newDirection = if (isBackCamera) Device.Descriptor.Position.FRONT else Device.Descriptor.Position.BACK
        val newCamera = context.getCamera(newDirection)
        val canFlip = !isVideoMuted && cameraDevice?.descriptor?.isValid == true
        Timber.d("Switching camera direction: $canFlip")
        if (!canFlip) return
        Timber.d("Switching camera direction from: $cameraDirection to: $newDirection")
        if (cameraDevice != null && newCamera != null) {
            _onPreviewUpdated.update { null }
            session?.exchangeDevices(cameraDevice!!, newCamera) { device ->
                Timber.d("Cameras exchanged from: ${cameraDevice?.descriptor?.friendlyName} to: ${device.descriptor.friendlyName}")
                isBackCamera = !isBackCamera
                cameraDevice = device
                displayCameraOutput()
            }
        }
    }

    fun toggleAudio() {
        Timber.d("Toggling audio state")
        isAudioMuted = !isAudioMuted
        if (isAudioMuted) {
            microphoneDevice?.let { device ->
                session?.detachDevice(device)
            }
        } else {
            microphoneDevice?.let { microphone ->
                attachDevice(microphone) { device ->
                    microphoneDevice = device.descriptor
                }
            }
        }
        _onAudioMuted.update { isAudioMuted }
    }

    fun toggleVideo(bitmap: Bitmap) {
        cameraOffBitmap = bitmap
        isVideoMuted = !isVideoMuted
        drawCameraOff(isVideoMuted)
        _onVideoMuted.update { isVideoMuted }
        Timber.d("Toggled video state: $isVideoMuted")
    }

    fun reloadDevices() {
        Timber.d("Reloading devices")
        session?.listAttachedDevices()?.forEach { device ->
            Timber.d("Detaching device: ${device.descriptor.deviceId}, ${device.descriptor.friendlyName}, ${device.descriptor.type}")
            session?.detachDevice(device)
        }
        session?.awaitDeviceChanges {
            Timber.d("Devices detached")
            cameraDevice?.descriptor?.let { camera ->
                attachDevice(camera) { device ->
                    cameraDevice = device
                    displayCameraOutput()
                    Timber.d("Camera re-attached")
                }
            }
            microphoneDevice?.let { microphone ->
                attachDevice(microphone) { device ->
                    microphoneDevice = device.descriptor
                    Timber.d("Microphone re-attached")
                }
            }
            session?.listAttachedDevices()?.forEach {
                Timber.d("Attached device: ${it.descriptor.deviceId}, ${it.descriptor.friendlyName}, ${it.descriptor.type}")
            }
        }
    }

    fun startScreenCapture(data: Intent?) {
        Timber.d("Starting screen capture")
        isScreenShareEnabled = true
        _onScreenShareEnabled.trySend(isScreenShareEnabled)
        slotNames.forEach { slot ->
            session?.mixer?.removeSlot(slot)?.takeIf { it }?.run {
                Timber.d("Slot: $slot removed")
            }
        }
        configuration.screenShareSlots.forEach { slot ->
            session?.mixer?.addSlot(slot)?.takeIf { it }?.run {
                Timber.d("Slot: ${slot.name} added")
            }
        }
        session?.createSystemCaptureSources(data, ScreenCaptureService::class.java, createNotification()) { devices ->
            devices.forEach { device ->
                val boundState = session?.mixer?.getDeviceBinding(device)
                Timber.d("Screen share device added: ${device.descriptor.friendlyName} to slot: $boundState")
            }
            screenDevices = devices
        }
    }

    fun stopScreenShare() {
        Timber.d("Stopping screen capture")
        isScreenShareEnabled = false
        _onScreenShareEnabled.trySend(isScreenShareEnabled)
        session?.stopSystemCapture()
        slotNames.forEach { slot ->
            session?.mixer?.removeSlot(slot)
        }
        session?.mixer?.addSlot(configuration.defaultSlot)
        screenDevices.forEach { device ->
            Timber.d("Detaching screen share device: ${device.descriptor.friendlyName}")
            session?.detachDevice(device)
        }
        screenDevices.clear()
        drawCameraOff(isVideoMuted)
    }

    fun displayCameraOutput() {
        (cameraDevice as? ImageDevice)?.getPreviewView(BroadcastConfiguration.AspectMode.FILL)?.run {
            launchMain {
                Timber.d("Camera output ready")
                layoutParams = ViewGroup.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                _onPreviewUpdated.update { this@run }
            }
        }
    }

    private fun createNotification(): Notification? {
        Timber.d("Creating notification")
        var notification: Notification? = null
        if (Build.VERSION.SDK_INT >= 26) {
            notification = session?.createServiceNotificationBuilder(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                Intent(context, NotificationActivity::class.java)
            )?.build()
        }
        return notification
    }

    private fun attachInitialDevices() {
        Timber.d("Attaching devices")
        var cameraFound = false
        var microphoneFound = false
        val availableCameras = mutableListOf<Device.Descriptor>()
        cameraOffDevice = session?.createImageInputSource()
        BroadcastSession.listAvailableDevices(context).forEach { descriptor ->
            if (descriptor.type == Device.Descriptor.DeviceType.CAMERA) {
                val isAcceptableDevice = (configuration.defaultCameraId != null
                        && configuration.defaultCameraId == descriptor.deviceId)
                        || configuration.defaultCameraId == null
                val hasCorrectFacing = isBackCamera && descriptor.position == Device.Descriptor.Position.BACK ||
                        !isBackCamera && descriptor.position == Device.Descriptor.Position.FRONT
                availableCameras.add(descriptor)
                if (isAcceptableDevice && !cameraFound && hasCorrectFacing) {
                    cameraFound = true
                    Timber.d("Attaching camera: ${descriptor.friendlyName}")
                    attachDevice(descriptor) { device ->
                        cameraDevice = device
                        displayCameraOutput()
                    }
                }
            }
            if (descriptor.type == Device.Descriptor.DeviceType.MICROPHONE && !microphoneFound) {
                microphoneFound = true
                Timber.d("Attaching mic: ${descriptor.friendlyName}")
                attachDevice(descriptor) { device ->
                    microphoneDevice = device.descriptor
                }
            }
        }
        _onDevicesListed.update { availableCameras.map { DeviceItem(it.type.name, it.deviceId, it.position.name) } }
        Timber.d("Initial devices attached: ${availableCameras.map { it.friendlyName }}")
    }

    private fun attachDevice(descriptor: Device.Descriptor, onAttached: (device: Device) -> Unit) {
        session?.attachDevice(descriptor) { device: Device ->
            session?.mixer?.bind(device, SLOT_DEFAULT)
            Timber.d("Device attached: ${device.descriptor.friendlyName}")
            onAttached(device)
        }
    }

    private fun drawCameraOff(isVideoMuted: Boolean) = launchMain {
        if (isVideoMuted) {
            Timber.d("Binding OFF device")
            session?.mixer?.unbind(cameraDevice)
            session?.mixer?.bind(cameraOffDevice, SLOT_DEFAULT)

            if (cameraOffBitmap == null) return@launchMain
            val canvas = cameraOffDevice?.inputSurface?.lockCanvas(null)
            val paint = Paint()
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(0, 0, 0)
            val centerX = (configuration.resolution.width - cameraOffBitmap!!.width) / 2
            val centerY = (configuration.resolution.height - cameraOffBitmap!!.height) / 2

            canvas?.drawRect(0f, 0f, configuration.resolution.width, configuration.resolution.height, paint)
            canvas?.drawBitmap(cameraOffBitmap!!, centerX, centerY, Paint())
            cameraOffDevice?.inputSurface?.unlockCanvasAndPost(canvas)
        } else {
            Timber.d("Binding Camera device")
            session?.mixer?.unbind(cameraOffDevice)
            session?.mixer?.bind(cameraDevice, SLOT_DEFAULT)
        }
        displayCameraOutput()
    }

    private fun resetTimer() {
        timeInSeconds = 0
        timerHandler.removeCallbacks(timerRunnable)
    }
}
