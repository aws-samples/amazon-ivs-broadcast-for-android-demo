package com.amazon.ivs.broadcast.ui.fragments.main

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context.MEDIA_PROJECTION_SERVICE
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnLayout
import androidx.core.view.drawToBitmap
import com.amazon.ivs.broadcast.App
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.common.*
import com.amazon.ivs.broadcast.common.broadcast.BroadcastState
import com.amazon.ivs.broadcast.databinding.FragmentMainBinding
import com.amazon.ivs.broadcast.models.Orientation
import com.amazon.ivs.broadcast.models.ui.DeviceHealth
import com.amazon.ivs.broadcast.models.ui.PopupModel
import com.amazon.ivs.broadcast.models.ui.PopupType
import com.amazon.ivs.broadcast.models.ui.StreamTopBarModel
import com.amazon.ivs.broadcast.ui.fragments.BaseFragment
import com.amazonaws.ivs.broadcast.BroadcastSession.State.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import timber.log.Timber

class MainFragment : BaseFragment() {

    private lateinit var binding: FragmentMainBinding
    private var isInPipMode = false

    private val bottomSheet: BottomSheetBehavior<View> by lazy {
        BottomSheetBehavior.from(binding.broadcastBottomSheet.root)
    }

    private val startForScreenShare =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Timber.d("Screen share started with result: ${result.resultCode}")
            if (result.resultCode == Activity.RESULT_OK) {
                bottomSheet.setCollapsed()
                viewModel.startScreenCapture(result.data)
            }
        }

    private var deviceHealth = DeviceHealth()
    private val deviceHealthHandler = Handler(Looper.getMainLooper())
    private var deviceHealthRunnable = object : Runnable {
        override fun run() {
            try {
                deviceHealth = DeviceHealth(
                    requireContext().getUsedMemory(),
                    requireContext().getCpuTemperature()
                )
                binding.deviceHealthUpdate = deviceHealth
            } finally {
                deviceHealthHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMainBinding.inflate(inflater, container, false)
        App.component.inject(this)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("onViewCreated")
        deviceHealthRunnable.run()

        preferences.isOnboardingDone = true
        configurationViewModel.resolution.orientation = configurationViewModel.orientationId
        updateControlPanelVisibility(requireContext().isViewLandscape(), true)
        if (!viewModel.isStreamOnline) {
            configurationViewModel.isLandscape = requireContext().isViewLandscape()
            viewModel.resetSession()
            viewModel.createSession()
            configurationViewModel.isConfigurationChanged = false
            binding.videoConfiguration = viewModel.currentConfiguration.video
            binding.topBarUpdate = StreamTopBarModel(streamStatus = DISCONNECTED)
        } else {
            binding.videoConfiguration = viewModel.currentConfiguration.video
            viewModel.reloadDevices()
        }

        binding.isStreamMuted = viewModel.isStreamMuted
        binding.isCameraOff = viewModel.isCameraOff
        binding.isScreenCaptureOn = viewModel.isScreenShareEnabled
        binding.useCustomResolution = configurationViewModel.useCustomResolution
        binding.broadcastBottomSheet.showDebugInfo.setVisible(configurationViewModel.developerMode)

        bottomSheet.peekHeight =
            if (configurationViewModel.developerMode) resources.getDimension(R.dimen.bottom_sheet_developer_peek_height)
                .toInt() else resources.getDimension(R.dimen.bottom_sheet_peek_height).toInt()

        if (configurationViewModel.useCustomResolution) {
            binding.streamFramerate.text = getString(R.string.fps_template, configurationViewModel.framerate)
            binding.broadcastSideSheet.streamFramerateLandscape.text =
                getString(R.string.fps_template, configurationViewModel.framerate)
            binding.streamQuality.text = getString(
                R.string.resolution_template,
                configurationViewModel.resolution.width.toInt(),
                configurationViewModel.resolution.height.toInt(),
            )
            binding.broadcastSideSheet.streamQualityLandscape.text = getString(
                R.string.resolution_template,
                configurationViewModel.resolution.width.toInt(),
                configurationViewModel.resolution.height.toInt(),
            )
        } else {
            binding.streamQuality.text = getString(
                R.string.quality_template,
                configurationViewModel.resolution.shortestSide.toInt(),
                configurationViewModel.framerate
            )
            binding.broadcastSideSheet.streamQualityLandscape.text = getString(
                R.string.quality_template,
                configurationViewModel.resolution.shortestSide.toInt(),
                configurationViewModel.framerate
            )
        }

        binding.isViewLandscape = requireContext().isViewLandscape()

        binding.streamSettings.setOnClickListener {
            onSettingsClick()
        }

        binding.broadcastSideSheet.landscapeSettingsButton.setOnClickListener {
            onSettingsClick()
        }

        binding.broadcastBottomSheet.showDebugInfo.setOnClickListener {
            bottomSheet.setCollapsed()
            binding.debugInfo.setVisible()
            binding.broadcastBottomSheet.showDebugInfo.setVisible(false, View.INVISIBLE)
        }

        binding.hideDebugInfo.setOnClickListener {
            binding.debugInfo.setVisible(false)
            binding.broadcastBottomSheet.showDebugInfo.setVisible()
        }

        binding.streamContainer.setOnClickListener {
            bottomSheet.state = when (bottomSheet.state) {
                BottomSheetBehavior.STATE_COLLAPSED -> BottomSheetBehavior.STATE_HIDDEN
                BottomSheetBehavior.STATE_HIDDEN -> BottomSheetBehavior.STATE_COLLAPSED
                else -> BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        binding.broadcastSideSheet.motionLayout.setVisible(requireActivity().isViewLandscape())

        binding.broadcastSideSheet.screenCaptureOn.setOnClickListener {
            updateSideSheetState()
        }
        binding.broadcastSideSheet.streamContainerLandscape.setOnClickListener {
            updateSideSheetState()
        }
        binding.broadcastBottomSheet.sendMetadata.setOnClickListener {
            onSendMetadataButtonClick()
        }
        binding.popupContainer.setOnClickListener {
            clearPopUp()
        }
        binding.broadcastBottomSheet.inviteToWatch.setOnClickListener {
            onInviteToWatchClick()
        }
        binding.broadcastSideSheet.inviteToWatch.setOnClickListener {
            onInviteToWatchClick()
        }
        binding.broadcastBottomSheet.broadcastMute.setOnClickListener {
            onMuteButtonClick()
        }
        binding.broadcastBottomSheet.broadcastCamera.setOnClickListener {
            onCameraButtonClick()
        }
        binding.broadcastBottomSheet.broadcastFlip.setOnClickListener {
            onFlipCameraButtonClick()
        }
        binding.broadcastBottomSheet.broadcastGoLive.setOnClickListener {
            onGoLiveButtonClick()
        }
        binding.broadcastBottomSheet.shareScreen.setOnClickListener {
            startScreenCapture()
        }
        binding.stopScreenShareCenterButton.setOnClickListener {
            viewModel.stopScreenShare()
        }
        binding.broadcastBottomSheet.stopScreenShareMenuButton.setOnClickListener {
            viewModel.stopScreenShare()
        }
        binding.broadcastSideSheet.stopScreenShareMenuButton.setOnClickListener {
            viewModel.stopScreenShare()
        }
        binding.broadcastSideSheet.stopScreenShareCenterButton.setOnClickListener {
            viewModel.stopScreenShare()
        }
        binding.broadcastSideSheet.broadcastCamera.setOnClickListener {
            onCameraButtonClick()
        }
        binding.broadcastSideSheet.broadcastFlip.setOnClickListener {
            onFlipCameraButtonClick()
        }
        binding.broadcastSideSheet.broadcastMute.setOnClickListener {
            onMuteButtonClick()
        }
        binding.broadcastSideSheet.broadcastGoLive.setOnClickListener {
            onGoLiveButtonClick()
        }
        binding.broadcastSideSheet.shareScreen.setOnClickListener {
            startScreenCapture()
        }
        binding.broadcastSideSheet.sendMetadata.setOnClickListener {
            onSendMetadataButtonClick()
        }

        binding.copyButton.setOnClickListener {
            val json = JsonObject()
            json.addProperty("MEM", deviceHealth.usedMemory)
            json.addProperty("TEMP", deviceHealth.cpuTemp)
            json.add("VideoConfig", Gson().toJsonTree(viewModel.currentConfiguration.video))
            copyToClipBoard(json.toString())
        }

        viewModel.onError.collectUI(this) { error ->
            showPopup(PopupModel(getString(R.string.error), getString(error.error), PopupType.ERROR))
        }

        viewModel.onScreenShareEnabled.collectUI(this) { isScreenCaptureOn ->
            Timber.d("On stream mode changed: Is screen share on $isScreenCaptureOn")
            binding.isScreenCaptureOn = isScreenCaptureOn
            changeMiniPlayerConstraints()
            when {
                isScreenCaptureOn && !viewModel.isStreamOnline -> showOfflineScreenShareAlert()
                !isScreenCaptureOn && !viewModel.isStreamOnline -> clearPopUp()
                !isScreenCaptureOn && viewModel.isStreamOnline && viewModel.isCameraOff -> {
                    binding.cameraOffSlotContainer.doOnLayout {
                        scaleToMatchResolution(it)
                    }
                }
            }
        }

        viewModel.onAudioMuted.collectUI(this) { muted ->
            binding.isStreamMuted = muted
        }

        viewModel.onVideoMuted.collectUI(this) { muted ->
            Timber.d("Video muted: $muted")
            if (muted) {
                binding.isCameraOff = muted
            }
        }

        viewModel.onBroadcastState.collectUI(this) { state ->
            when (state) {
                BroadcastState.BROADCAST_STARTED -> {
                    binding.topBarUpdate = StreamTopBarModel(
                        streamStatus = CONNECTING,
                        pillBackground = R.drawable.bg_connecting_pill
                    )
                }
                BroadcastState.BROADCAST_ENDED -> {
                    binding.topBarUpdate = StreamTopBarModel(
                        streamStatus = DISCONNECTED,
                        pillBackground = R.drawable.bg_offline_pill
                    )
                    if (viewModel.isScreenShareEnabled) {
                        showOfflineScreenShareAlert()
                    }
                }
                else -> { /* Ignore */ }
            }
        }

        viewModel.onStreamDataChanged.collectUI(this) { topBarModel ->
            binding.topBarUpdate = StreamTopBarModel(
                formattedTime = formatTime(topBarModel.seconds),
                formattedNetwork = formatTopBarNetwork(topBarModel.usedMegaBytes),
                streamStatus = CONNECTED,
                pillBackground = R.drawable.bg_online_pill
            )
            if (binding.popupUpdate?.type == PopupType.WARNING) {
                clearPopUp()
            }
        }

        viewModel.onPreviewUpdated.collectUI(this) { textureView ->
            switchStreamContainer(textureView)
            binding.isCameraOff = viewModel.isCameraOff
        }
    }

    private fun updateSideSheetState() {
        val transition = when (binding.broadcastSideSheet.motionLayout.currentState) {
            R.id.menu_full_open -> R.id.transition_full_open_to_half_open
            R.id.menu_half_open -> R.id.transition_half_open_to_close
            else -> R.id.transition_closed_to_half_open
        }
        binding.broadcastSideSheet.motionLayout.setTransition(transition)
        binding.broadcastSideSheet.motionLayout.transitionToEnd()
    }

    private fun onSettingsClick() {
        binding.defaultSlotContainer.removeAllViews()
        openFragment(R.id.navigation_settings)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        updateControlPanelVisibility(isLandscape)
        binding.isViewLandscape = isLandscape
        binding.constraintLayout.onDrawn {
            if (viewModel.isScreenShareEnabled) {
                changeMiniPlayerConstraints(isLandscape)
            }
            viewModel.onConfigurationChanged(isLandscape)
        }
        if (isLandscape) {
            binding.debugInfo.setVisible(false)
        }
    }

    private fun changeMiniPlayerConstraints(isLandscape: Boolean = requireContext().isViewLandscape()) {
        val miniContainerParams =
            binding.broadcastSideSheet.miniPreviewContainerLandscape.layoutParams as ConstraintLayout.LayoutParams
        miniContainerParams.clearAllAnchors()

        if (isLandscape) {
            miniContainerParams.rightToRight = ConstraintLayout.LayoutParams.UNSET
            miniContainerParams.leftToRight = binding.broadcastSideSheet.streamSideBar.id
            miniContainerParams.bottomToBottom = binding.constraintLayout.id
            miniContainerParams.bottomMargin = resources.getDimension(R.dimen.broadcast_margin_normal).toInt()
            miniContainerParams.leftMargin = resources.getDimension(R.dimen.broadcast_margin_normal).toInt()
        } else {
            miniContainerParams.leftToRight = ConstraintLayout.LayoutParams.UNSET
            miniContainerParams.rightToRight = binding.constraintLayout.id
            miniContainerParams.bottomToBottom = binding.constraintLayout.id
            miniContainerParams.bottomMargin =
                resources.getDimension(R.dimen.broadcast_mini_player_bottom_margin).toInt()
            miniContainerParams.marginEnd = resources.getDimension(R.dimen.broadcast_margin_big).toInt()
        }
        binding.broadcastSideSheet.miniPreviewContainerLandscape.layoutParams = miniContainerParams
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        isInPipMode = isInPictureInPictureMode
        viewModel.reloadDevices()
    }

    fun onBackPressed(): Boolean {
        return if (viewModel.isStreamOnline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
            params.setAspectRatio(
                Rational(
                    configurationViewModel.resolution.width.toInt(),
                    configurationViewModel.resolution.height.toInt()
                )
            )
            activity?.enterPictureInPictureMode(params.build())
            false
        } else {
            true
        }
    }

    private fun onInviteToWatchClick() {
        configurationViewModel.playbackUrl?.let { playbackUrl ->
            activity?.startShareIntent(playbackUrl)
        }
    }

    private fun updateControlPanelVisibility(isLandscape: Boolean, isInOnCreate: Boolean = false) {
        binding.broadcastBottomSheet.root.setVisible(!isLandscape)
        binding.broadcastSideSheet.motionLayout.setVisible(isLandscape)
        if (isLandscape) {
            val transition = when (bottomSheet.state) {
                BottomSheetBehavior.STATE_HIDDEN -> R.id.transition_half_open_to_close
                BottomSheetBehavior.STATE_COLLAPSED -> R.id.transition_closed_to_half_open
                else -> R.id.transition_half_open_to_full_open
            }

            binding.broadcastSideSheet.motionLayout.setTransition(transition)
        } else {
            bottomSheet.state = when (binding.broadcastSideSheet.motionLayout.currentState) {
                R.id.menu_full_open -> BottomSheetBehavior.STATE_EXPANDED
                R.id.menu_half_open -> BottomSheetBehavior.STATE_COLLAPSED
                else -> {
                    if (isInOnCreate) BottomSheetBehavior.STATE_COLLAPSED else BottomSheetBehavior.STATE_HIDDEN
                }
            }
            binding.broadcastSideSheet.motionLayout.setTransition(R.id.transition_half_open_to_close)
        }
        binding.broadcastSideSheet.motionLayout.progress = 1f
        binding.broadcastSideSheet.motionLayout.transitionToEnd()
    }

    private fun onSendMetadataButtonClick() {
        /*  binding.broadcastBottomSheet.sendMetadata.setVisible(false)
          binding.broadcastBottomSheet.metadataProgressBar.setVisible(true)
          if (viewModel.sendMetadata()) {
              showPopup(
                  PopupModel(
                      getString(R.string.success),
                      getString(R.string.sample_event_was_sent),
                      PopupType.SUCCESS
                  )
              )
          } else if (!viewModel.isStreamOnline) {
              showPopup(
                  PopupModel(
                      getString(R.string.error),
                      getString(R.string.must_be_streaming_to_send_event),
                      PopupType.ERROR
                  )
              )
          }
          binding.broadcastBottomSheet.sendMetadata.setVisible(true)
          binding.broadcastBottomSheet.metadataProgressBar.setVisible(false)*/
    }

    private fun onGoLiveButtonClick() {
        Timber.d("Will start stream: ${!viewModel.isStreamOnline}")
        if (viewModel.isStreamOnline) {
            viewModel.resetSession()
            viewModel.createSession()
        } else {
            viewModel.startStream()
        }
    }

    private fun onMuteButtonClick() {
        viewModel.toggleMute()
    }

    private fun onFlipCameraButtonClick() {
        binding.broadcastBottomSheet.broadcastFlip.disableAndEnable()
        binding.broadcastSideSheet.broadcastFlip.disableAndEnable()
        viewModel.switchCameraDirection()
    }

    private fun onCameraButtonClick() {
        binding.broadcastBottomSheet.broadcastCamera.disableAndEnable()
        binding.broadcastSideSheet.broadcastCamera.disableAndEnable()
        if (viewModel.isScreenShareEnabled) {
            binding.miniCameraOffSlotContainer.doOnLayout {
                viewModel.toggleCamera(binding.miniCameraOffSlotContainer.drawToBitmap())
            }
        } else {
            binding.cameraOffSlotContainer.doOnLayout {
                viewModel.toggleCamera(binding.cameraOffSlotContainer.drawToBitmap())
            }
        }
    }

    private fun switchStreamContainer(textureView: TextureView?) {
        binding.broadcastSideSheet.defaultSlotContainerLandscape.removeAllViews()
        binding.defaultSlotContainer.removeAllViews()
        binding.broadcastSideSheet.miniPreview.removeAllViews()
        binding.miniPreview.removeAllViews()
        binding.pipPreviewContainer.removeAllViews()
        if (textureView == null) return
        Timber.d("Add preview to container")
        when {
            isInPipMode -> binding.pipPreviewContainer.addView(textureView)
            !isInPipMode && viewModel.isScreenShareEnabled -> {
                if (requireContext().isViewLandscape()) {
                    binding.broadcastSideSheet.miniPreview.addView(textureView)
                } else {
                    binding.miniPreview.addView(textureView)
                }
            }
            else -> {
                if (configurationViewModel.orientationId != Orientation.AUTO.id) {
                    scaleToMatchResolution(textureView)
                }
                if (requireContext().isViewLandscape()) {
                    binding.broadcastSideSheet.defaultSlotContainerLandscape.addView(textureView)
                } else {
                    Timber.d("Adding preview to default slot container")
                    binding.defaultSlotContainer.addView(textureView)
                }
            }
        }
    }

    private fun startScreenCapture() {
        (requireContext().getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager)?.run {
            startForScreenShare.launch(createScreenCaptureIntent())
        }
    }

    private fun showOfflineScreenShareAlert() {
        showPopup(
            PopupModel(
                getString(R.string.alert),
                getString(R.string.offline_screen_sharing_alert),
                PopupType.WARNING
            ),
            false
        )
    }

    private fun showPopup(popupUpdateModel: PopupModel, setTimer: Boolean = true) {
        binding.popupUpdate = popupUpdateModel
        binding.popupContainer.setVisible()
        if (setTimer) {
            launchUI {
                delay(POPUP_DURATION)
                clearPopUp()
            }
        }
    }

    private fun clearPopUp() {
        binding.popupContainer.setVisible(false)
    }

    private fun scaleToMatchResolution(view: View) {
        val container = if (requireContext().isViewLandscape()) binding.broadcastSideSheet.streamContainerLandscape else
            binding.streamContainer
        val screenWidth = container.width
        val screenHeight = container.height
        var width = 1 * configurationViewModel.resolution.widthAgainstHeightRatio
        var height = 1

        while (width < screenWidth && height < screenHeight) {
            width += 1 * configurationViewModel.resolution.widthAgainstHeightRatio
            height += 1
        }

        view.layoutParams.width = width.toInt()
        view.layoutParams.height = height
        binding.broadcastSideSheet.streamContainerCardview.layoutParams.width = width.toInt()
        binding.broadcastSideSheet.streamContainerCardview.layoutParams.height = height
        Timber.d("Screen size: $screenWidth, $screenHeight, Video size: $width, $height")
    }
}
