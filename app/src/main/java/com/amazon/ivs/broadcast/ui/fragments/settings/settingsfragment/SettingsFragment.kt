package com.amazon.ivs.broadcast.ui.fragments.settings.settingsfragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.common.AMAZON_IVS_URL
import com.amazon.ivs.broadcast.common.POPUP_DURATION
import com.amazon.ivs.broadcast.common.createLinks
import com.amazon.ivs.broadcast.common.getOrientation
import com.amazon.ivs.broadcast.common.launchUI
import com.amazon.ivs.broadcast.common.openFragment
import com.amazon.ivs.broadcast.common.setVisible
import com.amazon.ivs.broadcast.common.showCameraDialog
import com.amazon.ivs.broadcast.common.showInputDialog
import com.amazon.ivs.broadcast.common.toFormattedKbps
import com.amazon.ivs.broadcast.common.viewBinding
import com.amazon.ivs.broadcast.databinding.FragmentSettingsBinding
import com.amazon.ivs.broadcast.models.ui.PopupModel
import com.amazon.ivs.broadcast.models.ui.PopupType
import com.amazon.ivs.broadcast.ui.fragments.BaseFragment
import com.amazon.ivs.broadcast.ui.fragments.autoconfiguration.AutoConfigurationViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import timber.log.Timber

@AndroidEntryPoint
class SettingsFragment : BaseFragment(R.layout.fragment_settings) {
    private val binding by viewBinding(FragmentSettingsBinding::bind)
    private val autoConfigurationViewModel by activityViewModels<AutoConfigurationViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.orientationValue.text = configurationViewModel.orientationId.getOrientation().value
        binding.developerModeSwitch.isChecked = configurationViewModel.developerMode
        binding.ingestServerUrlValue.text = if (configurationViewModel.ingestServerUrl.isNullOrBlank()) {
            getString(R.string.not_set)
        } else {
            configurationViewModel.ingestServerUrl
        }
        binding.streamKeyValue.text = if (configurationViewModel.streamKey.isNullOrEmpty()) {
            getString(R.string.not_set)
        } else {
            getString(R.string.concealed_stream_key)
        }
        binding.playbackUrlValue.text = if (configurationViewModel.playbackUrl.isNullOrEmpty()) {
            getString(R.string.not_set)
        } else {
            configurationViewModel.playbackUrl
        }

        configurationViewModel.resetDefaultCamera()
        configurationViewModel.defaultDeviceItem?.let { camera ->
            binding.defaultCameraValue.text = getString(
                R.string.camera_option_template,
                camera.type,
                camera.deviceId,
                camera.direction
            )
        }

        binding.bitrateValue.text = toFormattedKbps(
            configurationViewModel.targetBitrate,
            if (configurationViewModel.autoAdjustBitrate) R.string.bitrate_template_auto else R.string.kbps_template,
        )

        binding.resolutionAndFramerateValue.text = with(configurationViewModel.resolution) {
            if (configurationViewModel.useCustomResolution) {
                getString(R.string.custom_resolution_template, width.toInt(), height.toInt())
            } else {
                getString(
                    R.string.quality_template,
                    shortestSide.toInt(),
                    configurationViewModel.framerate
                )
            }
        }

        binding.backButton.setOnClickListener {
            openFragment(R.id.navigation_main)
        }

        binding.resolutionAndFramerate.setOnClickListener {
            openFragment(R.id.navigation_stream_graphic_properties)
        }

        binding.orientation.setOnClickListener {
            openFragment(R.id.navigation_stream_graphic_properties)
        }

        binding.bitrate.setOnClickListener {
            openFragment(R.id.navigation_stream_bitrate_properties)
        }

        binding.popupContainer.setOnClickListener {
            clearPopUp()
        }

        binding.autoConfig.setOnClickListener {
            autoConfigurationViewModel.isRanFromSettingsView = true
            autoConfigurationViewModel.rerunConfiguration = true

            if (binding.ingestServerUrlValue.text != getString(R.string.not_set)
                && binding.streamKeyValue.text != getString(R.string.not_set)
            ) {
                openFragment(R.id.navigation_configuration_setup)
            } else {
                showPopup(
                    PopupModel(
                        getString(R.string.error),
                        getString(R.string.incomplete_stream_credentials_message),
                        PopupType.ERROR
                    )
                )
            }
        }

        binding.developerModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            configurationViewModel.developerMode = isChecked
        }

        binding.settingsDeveloperModeContainer.setOnClickListener {
            binding.developerModeSwitch.isChecked = !binding.developerModeSwitch.isChecked
        }

        binding.ingestServerContainer.setOnClickListener {
            binding.root.showInputDialog(
                getString(R.string.ingest_server),
                inputHint = getString(R.string.endpoint_url_base),
                inputText = binding.ingestServerUrlValue.text.toString(),
            ) { serverUrl ->
                binding.ingestServerUrlValue.text = if (serverUrl.isBlank()) {
                    resources.getString(R.string.not_set)
                } else {
                    configurationViewModel.ingestServerUrl = serverUrl
                    serverUrl
                }
            }
        }

        binding.playbackUrlContainer.setOnClickListener {
            binding.root.showInputDialog(
                getString(R.string.playback_url),
                inputHint = getString(R.string.playback_url_hint),
                inputText = binding.playbackUrlValue.text.toString(),
            ) { playbackUrl ->
                binding.playbackUrlValue.text = if (playbackUrl.isBlank()) {
                    resources.getString(R.string.not_set)
                } else {
                    configurationViewModel.playbackUrl = getString(R.string.playback_url_template, playbackUrl)
                    playbackUrl
                }
            }
        }

        binding.streamKeyContainer.setOnClickListener {
            binding.root.showInputDialog(
                getString(R.string.stream_key),
                getString(R.string.stream_key_input_note),
                getString(R.string.stream_key),
                configurationViewModel.streamKey,
            ) { streamKey ->
                if (streamKey.isBlank()) {
                    binding.streamKeyValue.text = getString(R.string.not_set)
                } else {
                    binding.streamKeyValue.text = getString(R.string.concealed_stream_key)
                    configurationViewModel.streamKey = streamKey
                }

            }
        }

        binding.createChannel.createLinks(Pair(getString(R.string.link_amazon_channel)) {
            configurationViewModel.webViewUrl = AMAZON_IVS_URL
            openFragment(R.id.navigation_web_view)
        })

        binding.defaultCameraContainer.setOnClickListener {
            binding.root.showCameraDialog(getString(R.string.orientation), configurationViewModel.camerasList) { option ->
                Timber.d("Default camera selected: $option")
                binding.defaultCameraValue.text = getString(
                    R.string.camera_option_template,
                    option.type,
                    option.deviceId,
                    option.direction
                )
                configurationViewModel.defaultCameraId = option.deviceId
                configurationViewModel.defaultCameraPosition = option.direction
            }
        }
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
}
