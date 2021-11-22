package com.amazon.ivs.broadcast.common.broadcast

import android.app.Application
import android.app.Notification
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
import com.amazon.ivs.broadcast.common.*
import com.amazon.ivs.broadcast.models.ui.DeviceItem
import com.amazon.ivs.broadcast.models.ui.StreamTopBarModel
import com.amazon.ivs.broadcast.ui.activities.NotificationActivity
import com.amazon.ivs.broadcast.ui.fragments.ConfigurationViewModel
import com.amazonaws.ivs.broadcast.*
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

private const val NOTIFICATION_CHANNEL_ID = "notificationId"
private const val NOTIFICATION_CHANNEL_NAME = "notificationName"

enum class BroadcastError(val error: Int) {
    FATAL(R.string.error_fatal),
    DEVICE_DISCONNECTED(R.string.error_device_disconnected)
}

enum class BroadcastState {
    BROADCAST_STARTING,
    BROADCAST_STARTED,
    BROADCAST_ENDED,
    BROADCAST_UPDATED,
}

class BroadcastManager(private val context: Application) {

    private val isBackCamera get() = cameraDevice?.descriptor?.position == Device.Descriptor.Position.BACK
    private val cameraDirection get() = if (isBackCamera) Device.Descriptor.Position.BACK else Device.Descriptor.Position.FRONT

    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable = object : Runnable {
        override fun run() {
            try {
                val usedMegaBytes = getSessionUsedBytes(startBytes) / BYTES_TO_MEGABYTES_FACTOR
                timeInSeconds += 1
                _onStreamDataChanged.tryEmit(StreamTopBarModel(seconds = timeInSeconds, usedMegaBytes = usedMegaBytes))
            } finally {
                timerHandler.postDelayed(this, 1000)
            }
        }
    }
    private var startBytes = 0f
    private var timeInSeconds = 0

    private var _onError = ConsumableSharedFlow<BroadcastError>()
    private var _onEvent = ConsumableSharedFlow<BroadcastState>()
    private var _onPreviewUpdated = ConsumableSharedFlow<TextureView>()
    private var _onAudioMuted = ConsumableSharedFlow<Boolean>()
    private var _onVideoMuted = ConsumableSharedFlow<Boolean>()
    private var _onScreenShareEnabled = ConsumableSharedFlow<Boolean>()
    private var _onStreamDataChanged = ConsumableSharedFlow<StreamTopBarModel>()
    private var _onDevicesListed = ConsumableSharedFlow<List<DeviceItem>>()

    private lateinit var configuration: ConfigurationViewModel
    private var session: BroadcastSession? = null
    private var cameraDevice: Device? = null
    private var cameraOffDevice: SurfaceSource? = null
    private var microphoneDevice: Device.Descriptor? = null
    private var cameraOffBitmap: Bitmap? = null
    private val devices = mutableListOf<Device.Descriptor>()
    private var screenDevices = mutableListOf<Device>()

    lateinit var currentConfiguration: BroadcastConfiguration
        private set
    var isScreenShareEnabled = false
        private set
    var isVideoMuted = false
        private set
    var isAudioMuted = false
        private set
    val isStreamOnline get() = onBroadcastState.replayCache.lastOrNull() == BroadcastState.BROADCAST_STARTED

    val onError = _onError.asSharedFlow()
    val onBroadcastState = _onEvent.asSharedFlow()
    val onPreviewUpdated = _onPreviewUpdated.asSharedFlow()
    val onAudioMuted = _onAudioMuted.asSharedFlow()
    val onVideoMuted = _onVideoMuted.asSharedFlow()
    val onScreenShareEnabled = _onScreenShareEnabled.asSharedFlow()
    val onStreamDataChanged = _onStreamDataChanged.asSharedFlow()
    val onDevicesListed = _onDevicesListed.asSharedFlow()

    fun init(configuration: ConfigurationViewModel) {
        this.configuration = configuration
    }

    fun createSession() {
        currentConfiguration = configuration.newestConfiguration
        Timber.d("Creating session with configuration: ${currentConfiguration.asString()}")
        startBytes = (TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()).toFloat()
        attachInitialDevices()
        session = BroadcastSession(context, object: BroadcastSession.Listener() {
            override fun onStateChanged(state: BroadcastSession.State) {
                Timber.d("Broadcast state changed: $state")
                when (state) {
                    BroadcastSession.State.INVALID,
                    BroadcastSession.State.DISCONNECTED,
                    BroadcastSession.State.ERROR -> {
                        _onEvent.emitNew(BroadcastState.BROADCAST_ENDED)
                        resetTimer()
                    }
                    BroadcastSession.State.CONNECTING -> _onEvent.emitNew(BroadcastState.BROADCAST_STARTING)
                    BroadcastSession.State.CONNECTED -> {
                        _onEvent.emitNew(BroadcastState.BROADCAST_STARTED)
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
                            _onError.tryEmit(BroadcastError.DEVICE_DISCONNECTED)
                        }
                    }
                } else if (error.error == ErrorType.ERROR_DEVICE_DISCONNECTED && microphoneDevice == null) {
                    _onError.tryEmit(BroadcastError.DEVICE_DISCONNECTED)
                } else if (error.isFatal) {
                    error.printStackTrace()
                    _onError.tryEmit(BroadcastError.FATAL)
                }
            }
        }, currentConfiguration, devices.toTypedArray())
        session?.run {
            awaitDeviceChanges {
                _onDevicesListed.tryEmit(context.listAvailableCameras().map {
                    DeviceItem(it.type.name, it.deviceId, it.position.name)
                })
                configuration.defaultCameraId?.let { cameraId ->
                    attachCamera(cameraId) { device ->
                        Timber.d("Pre-selected camera attached with ID: ${device.descriptor.deviceId}")
                        displayCameraOutput(device)
                    }
                }
                listAttachedDevices().forEach { device ->
                    if (device.descriptor.type == Device.Descriptor.DeviceType.CAMERA
                        && configuration.defaultCameraId == null) {
                        Timber.d("Attached camera found with ID: ${device.descriptor.deviceId}")
                        cameraDevice = device
                        session?.mixer?.bind(device, SLOT_DEFAULT)
                        displayCameraOutput(device)
                    }

                    if (device.descriptor.type == Device.Descriptor.DeviceType.MICROPHONE) {
                        Timber.d("Attached microphone found with ID: ${device.descriptor.deviceId}")
                        microphoneDevice = device.descriptor
                        session?.mixer?.bind(device, SLOT_DEFAULT)
                    }
                    cameraOffDevice = session?.createImageInputSource()
                }
                // TODO: Without calling the `reloadDevices` function the app will freeze and make samsung S10 crash
                //  But - on OnePlus 7 PRO - if the function is called then the preview won't be drawn but it works fine without
                reloadDevices()
            }
        }
        Timber.d("Session created")
    }

    fun resetSession() {
        session?.run {
            Timber.d("Releasing session")
            stop()
            release()
        }
        session = null
    }

    fun startStream() {
        Timber.d("Starting stream: ${configuration.ingestServerUrl}, ${configuration.streamKey}")
        session?.start(configuration.ingestServerUrl, configuration.streamKey)
    }

    fun flipCameraDirection() {
        val newDirection = if (isBackCamera) Device.Descriptor.Position.FRONT else Device.Descriptor.Position.BACK
        val newCamera = context.getCamera(newDirection)
        val canFlip = !isVideoMuted && cameraDevice?.isValid == true
        Timber.d("Switching camera direction: $canFlip")
        if (!canFlip) return
        Timber.d("Switching camera direction from: $cameraDirection to: $newDirection")
        cameraDevice?.run {
            Timber.d("Detaching old camera: ${descriptor.friendlyName}")
            session?.detachDevice(this)
        }
        newCamera?.run {
            Timber.d("Attaching new camera: $friendlyName")
            session?.attachDevice(this) { camera ->
                displayCameraOutput(camera)
                cameraDevice = camera
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
            attachMic(microphoneDevice?.deviceId)
        }
        _onAudioMuted.tryEmit(isAudioMuted)
    }

    fun toggleVideo(bitmap: Bitmap) {
        cameraOffBitmap = bitmap
        isVideoMuted = !isVideoMuted
        if (isVideoMuted) {
            drawCameraOff()
        } else {
            reloadDevices()
        }
        _onVideoMuted.tryEmit(isVideoMuted)
        Timber.d("Toggled video state: $isVideoMuted")
    }

    fun reloadDevices() {
        Timber.d("Reloading devices")
        if (isVideoMuted) {
            session?.mixer?.removeSlot(SLOT_DEFAULT)
        } else {
            if (isScreenShareEnabled) {
                session?.mixer?.addSlot(configuration.screenShareSlots[0])
            } else {
                session?.mixer?.addSlot(configuration.defaultSlot)
            }
        }
        session?.listAttachedDevices()?.forEach { device ->
            Timber.d("Detaching device: ${device.descriptor.deviceId}, ${device.descriptor.friendlyName}, ${device.descriptor.type}")
            session?.detachDevice(device)
        }
        session?.awaitDeviceChanges {
            Timber.d("Devices detached")
            cameraDevice?.descriptor?.run {
                attachCamera(deviceId) { device ->
                    displayCameraOutput(device)
                }
            }
            microphoneDevice?.run {
                attachMic(deviceId)
            }
            session?.listAttachedDevices()?.forEach {
                Timber.d("Attached device: ${it.descriptor.deviceId}, ${it.descriptor.friendlyName}, ${it.descriptor.type}")
            }
            _onEvent.emitNew(BroadcastState.BROADCAST_UPDATED)
        }
    }

    fun startScreenCapture(data: Intent?) {
        Timber.d("Starting screen capture")
        isScreenShareEnabled = true
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
            reloadDevices()
        }
    }

    fun stopScreenShare() {
        Timber.d("Stopping screen capture")
        isScreenShareEnabled = false
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
        if (isVideoMuted) {
            drawCameraOff()
        } else {
            reloadDevices()
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
        var camera: Device.Descriptor? = null
        var microphone: Device.Descriptor? = null
        BroadcastSession.listAvailableDevices(context).forEach { device ->
            if (device.type == Device.Descriptor.DeviceType.CAMERA) {
                camera = device
            }
            if (device.type == Device.Descriptor.DeviceType.MICROPHONE) {
                microphone = device
            }
        }
        devices.clear()
        if (camera != null) devices.add(camera!!)
        if (microphone != null) devices.add(microphone!!)
        Timber.d("Initial devices attached: $camera, $microphone")
    }

    private fun drawCameraOff() = launchMain {
        if (cameraOffBitmap == null) return@launchMain
        Timber.d("Showing camera off")
        cameraOffDevice?.run {
            session?.detachDevice(this)
        }
        val canvas = cameraOffDevice?.inputSurface?.lockCanvas(null)
        val paint = Paint()
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(0, 0, 0)

        canvas?.drawRect(
            0f,
            0f,
            configuration.resolution.width,
            configuration.resolution.height,
            paint
        )
        canvas?.let {
            canvas.drawBitmap(
                cameraOffBitmap!!,
                (configuration.resolution.width - cameraOffBitmap!!.width) / 2,
                (configuration.resolution.height - cameraOffBitmap!!.height) / 2,
                Paint()
            )
        }

        cameraOffDevice?.inputSurface?.unlockCanvasAndPost(canvas)
        val binding = session?.mixer?.getDeviceBinding(cameraOffDevice)
        if (binding != SLOT_DEFAULT) {
            Timber.d("Binding camera off device to default slot")
            session?.mixer?.bind(cameraOffDevice, SLOT_DEFAULT)
        }
        reloadDevices()
    }

    private fun attachCamera(cameraId: String? = null, onAttached: (device: Device) -> Unit) {
        if (session?.isReady == true && !isVideoMuted) {
            for (descriptor in BroadcastSession.listAvailableDevices(context)) {
                if (descriptor.deviceId == cameraId) {
                    session?.attachDevice(descriptor) { device: Device ->
                        Timber.d("Camera attached: ${device.descriptor.friendlyName}")
                        session?.mixer?.bind(device, SLOT_DEFAULT)
                        cameraDevice = device
                        onAttached(device)
                    }
                    break
                }
            }
        }
    }

    private fun attachMic(id: String? = "", onAttached: (device: Device) -> Unit = {}) {
        if (session?.isReady == true && !isAudioMuted) {
            for (descriptor in BroadcastSession.listAvailableDevices(context)) {
                if (descriptor.deviceId == id && (descriptor.type === Device.Descriptor.DeviceType.MICROPHONE)) {
                    session?.attachDevice(descriptor) { device: Device ->
                        Timber.d("Microphone attached: ${device.descriptor.friendlyName}")
                        session?.mixer?.bind(device, SLOT_DEFAULT)
                        microphoneDevice = device.descriptor
                        onAttached(device)
                    }
                    break
                }
            }
        }
    }

    private fun displayCameraOutput(device: Device) {
        device as ImageDevice
        device.getPreviewView(BroadcastConfiguration.AspectMode.FILL)?.run {
            launchMain {
                Timber.d("Camera output ready")
                layoutParams = ViewGroup.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                _onPreviewUpdated.tryEmit(this@run)
            }
        }
    }

    private fun resetTimer() {
        timeInSeconds = 0
        timerHandler.removeCallbacks(timerRunnable)
    }
}
