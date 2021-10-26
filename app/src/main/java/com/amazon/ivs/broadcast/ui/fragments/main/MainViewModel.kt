package com.amazon.ivs.broadcast.ui.fragments.main

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
import android.util.Pair
import android.view.TextureView
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.amazon.ivs.broadcast.common.*
import com.amazon.ivs.broadcast.common.broadcast.*
import com.amazon.ivs.broadcast.models.CameraDirection
import com.amazon.ivs.broadcast.models.ui.DeviceHealth
import com.amazon.ivs.broadcast.models.ui.DeviceItem
import com.amazon.ivs.broadcast.models.ui.StreamTopBarModel
import com.amazon.ivs.broadcast.ui.activities.NotificationActivity
import com.amazon.ivs.broadcast.ui.fragments.ConfigurationViewModel
import com.amazonaws.ivs.broadcast.*
import com.amazonaws.ivs.broadcast.Presets.Devices.*
import timber.log.Timber

private const val NOTIFICATION_CHANNEL_ID = "notificationId"
private const val NOTIFICATION_CHANNEL_NAME = "notificationName"

class MainViewModel(
    private val context: Application,
    private val configurationViewModel: ConfigurationViewModel
) : AndroidViewModel(context) {

    var startBytes = 0f
    var isCameraOff = false
    var isStreamMuted = false
    var session: BroadcastSession? = null

    val cameraPreview = ConsumableLiveData<TextureView>()
    val onStreamStatusChanged = ConsumableLiveData<BroadcastSession.State>()
    val errorHappened = ConsumableLiveData<Pair<String, String>>()
    val onStreamDataChanged = ConsumableLiveData<StreamTopBarModel>()
    val onDeviceHealthUpdate = ConsumableLiveData<DeviceHealth>()
    val isScreenShareEnabled = ConsumableLiveData<Boolean>()
    val sessionConfiguration = ConsumableLiveData<BroadcastConfiguration>()
    val onDevicesReloaded = ConsumableLiveData<Unit>()

    private var cameraOffDevice: SurfaceSource? = null
    private var screenDevices = mutableListOf<Device>()
    private var cameraDevice: Device? = null
    private var microphoneDevice: Device.Descriptor? = null
    private var timeInSeconds = 0
    private var cameraOffBitmap: Bitmap? = null

    private val cameraDirection get() = if (cameraDevice?.descriptor?.position == CameraDirection.BACK.broadcast)
        CameraDirection.BACK else CameraDirection.FRONT
    private val isBackDirection get() = configurationViewModel.defaultCameraPosition == Device.Descriptor.Position.BACK.name
    private val initialDevices get() = listOf(
        if (cameraDirection == CameraDirection.BACK || isBackDirection)
            BACK_CAMERA(context)[0] else FRONT_CAMERA(context)[0],
        MICROPHONE(context)[0]
    ).toTypedArray()

    private val timerHandler = Handler(Looper.getMainLooper())
    private val deviceHealthHandler = Handler(Looper.getMainLooper())

    private var timerRunnable = object : Runnable {
        override fun run() {
            try {
                val usedMegaBytes = getSessionUsedBytes(startBytes) / BYTES_TO_MEGABYTES_FACTOR
                timeInSeconds += 1
                onStreamDataChanged.postConsumable(
                    StreamTopBarModel(
                        seconds = timeInSeconds,
                        usedMegaBytes = usedMegaBytes
                    )
                )
            } finally {
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private var deviceHealthRunnable = object : Runnable {
        override fun run() {
            try {
                launchIO {
                    onDeviceHealthUpdate.postConsumable(
                        DeviceHealth(
                            context.getUsedMemory(),
                            context.getCpuTemperature()
                        )
                    )
                }
            } finally {
                deviceHealthHandler.postDelayed(this, 1000)
            }
        }
    }

    private val broadcastListener by lazy {
        (object : BroadcastSession.Listener() {
            override fun onStateChanged(state: BroadcastSession.State) {
                launchMain {
                    when (state) {
                        BroadcastSession.State.CONNECTED -> timerRunnable.run()
                        else -> resetTimer()
                    }
                    Timber.d("$state")
                    onStreamStatusChanged.postConsumable(state)
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
                Timber.d("Error: ${error.detail} Error code: ${error.code} Error source: ${error.source}")
                if (error.error == ErrorType.ERROR_DEVICE_DISCONNECTED && error.source == microphoneDevice?.urn) {
                    microphoneDevice?.let {
                        try {
                            session?.exchangeDevices(it, it) { microphone ->
                                Timber.d("Device with id ${microphoneDevice?.deviceId} reattached")
                                microphoneDevice = microphone.descriptor
                            }
                        } catch (e: BroadcastException) {
                            Timber.d("Microphone exchange exception $e")
                        }
                    }
                } else if (error.error == ErrorType.ERROR_DEVICE_DISCONNECTED && microphoneDevice == null) {
                    launchMain {
                        Toast.makeText(context, "External device ${error.source} disconnected", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else if (error.isFatal) {
                    error.printStackTrace()
                    launchMain { errorHappened.postConsumable(Pair(error.code.toString(), error.detail)) }
                }
            }
        })
    }

    init {
        deviceHealthRunnable.run()
    }

    private fun createSession() {
        Timber.d("Creating session with configuration: ${configurationViewModel.newestConfiguration.asString()}")
        BroadcastSession(context, broadcastListener, configurationViewModel.newestConfiguration, initialDevices).apply {
            Timber.d("Session created")
            sessionConfiguration.postConsumable(configurationViewModel.newestConfiguration)
            startBytes = (TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()).toFloat()
            session = this

            awaitDeviceChanges {
                configurationViewModel.camerasList = context.listAvailableCameras().map {
                    DeviceItem(it.type.name, it.deviceId, it.position.name)
                }
                configurationViewModel.defaultCameraId?.let { cameraId ->
                    attachCamera(cameraId) { device ->
                        Timber.d("Pre-selected camera attached with ID: ${device.descriptor.deviceId}")
                        displayCameraOutput(device)
                    }
                }
                listAttachedDevices().forEach { device ->
                    if (device.descriptor.type == Device.Descriptor.DeviceType.CAMERA
                            && configurationViewModel.defaultCameraId == null) {
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
            }
        }
    }

    fun switchCameraDirection() {
        val newDirection = if (cameraDirection == CameraDirection.FRONT) CameraDirection.BACK else CameraDirection.FRONT
        val newCamera = context.getCamera(newDirection.broadcast)
        if (isCameraOff || cameraDevice == null || cameraDevice?.isValid == false) return
        Timber.d("Switching camera direction from: $cameraDirection to: $newDirection")
        session?.listAttachedDevices()?.forEach {
            Timber.d("Attached device: ${it.descriptor.deviceId}, ${it.descriptor.friendlyName}")
        }
        cameraDevice?.run {
            Timber.d("Detaching old camera: ${descriptor.friendlyName}")
            session?.detachDevice(this)
        }
        newCamera?.run {
            Timber.d("Attaching new camera: $friendlyName")
            session?.attachDevice(this) { camera ->
                displayCameraOutput(camera)
                cameraDevice = camera
                reloadDevices()
            }
        }
    }

    fun toggleMute(onToggle: (muted: Boolean) -> Unit) {
        Timber.d("Toggling audio state")
        isStreamMuted = !isStreamMuted
        if (isStreamMuted) {
            microphoneDevice?.let { device ->
                session?.detachDevice(device)
            }
        } else {
            attachMic(microphoneDevice?.deviceId)
        }
        onToggle(isStreamMuted)
    }

    fun toggleCamera(bitmap: Bitmap) {
        cameraOffBitmap = bitmap
        isCameraOff = !isCameraOff
        if (isCameraOff) {
            drawCameraOff()
        } else {
            reloadDevices()
        }
        Timber.d("Toggled video state: $isCameraOff")
    }

    fun onConfigurationChanged(isLandscape: Boolean) {
        Timber.d("Configuration changed")
        configurationViewModel.isLandscape = isLandscape
        reloadDevices()
    }

    fun reloadDevices() {
        Timber.d("Reloading devices")
        val cameraToAttach = if (isCameraOff) cameraOffDevice?.descriptor else cameraDevice?.descriptor
        if (isCameraOff) {
            session?.mixer?.removeSlot(SLOT_DEFAULT)
        } else {
            if (isScreenCaptureEnabled()) {
                session?.mixer?.addSlot(configurationViewModel.screenShareSlots[0])
            } else {
                session?.mixer?.addSlot(configurationViewModel.defaultSlot)
            }
        }
        session?.listAttachedDevices()?.forEach { device ->
            Timber.d("Detaching device: ${device.descriptor.deviceId}, ${device.descriptor.friendlyName}, ${device.descriptor.type}")
            session?.detachDevice(device)
        }
        session?.awaitDeviceChanges {
            Timber.d("Devices detached")
            cameraToAttach?.run {
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
            onDevicesReloaded.postConsumable(Unit)
        }
    }

    fun startScreenCapture(data: Intent?) {
        Timber.d("Starting screen capture")
        isScreenShareEnabled.postConsumable(true)
        slotNames.forEach { slot ->
            session?.mixer?.removeSlot(slot)?.takeIf { it }?.run {
                Timber.d("Slot: $slot removed")
            }
        }
        configurationViewModel.screenShareSlots.forEach { slot ->
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
        isScreenShareEnabled.postConsumable(false)
        session?.stopSystemCapture()
        slotNames.forEach { slot ->
            session?.mixer?.removeSlot(slot)
        }
        session?.mixer?.addSlot(configurationViewModel.defaultSlot)
        screenDevices.forEach { device ->
            Timber.d("Detaching screen share device: ${device.descriptor.friendlyName}")
            session?.detachDevice(device)
        }
        screenDevices.clear()
        if (isCameraOff) {
            drawCameraOff()
        } else {
            reloadDevices()
        }
    }

    fun resetSession() {
        session?.run {
            Timber.d("Releasing session")
            release()
        }
        session = null
        createSession()
    }

    fun startStream() {
        Timber.d("Starting stream: ${configurationViewModel.ingestServerUrl}, ${configurationViewModel.streamKey}")
        session?.start(configurationViewModel.ingestServerUrl, configurationViewModel.streamKey)
    }

    fun isStreamOnline() = onStreamStatusChanged.consumedValue == BroadcastSession.State.CONNECTED

    fun isScreenCaptureEnabled() = isScreenShareEnabled.consumedValue == true

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
            configurationViewModel.resolution.width,
            configurationViewModel.resolution.height,
            paint
        )
        canvas?.let {
            canvas.drawBitmap(
                cameraOffBitmap!!,
                (configurationViewModel.resolution.width - cameraOffBitmap!!.width) / 2,
                (configurationViewModel.resolution.height - cameraOffBitmap!!.height) / 2,
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
        if (session?.isReady == true) {
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
        if (session?.isReady == true) {
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

    private fun displayCameraOutput(device: Device) {
        device as ImageDevice
        device.getPreviewView(BroadcastConfiguration.AspectMode.FILL).run {
            launchMain {
                Timber.d("Camera output ready")
                layoutParams = ViewGroup.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                cameraPreview.postConsumable(this@run)
            }
        }
    }

    private fun resetTimer() {
        timeInSeconds = 0
        timerHandler.removeCallbacks(timerRunnable)
    }
}
