package com.amazon.ivs.broadcast.ui.fragments.autoconfiguration.configurationsetup

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.common.*
import com.amazon.ivs.broadcast.databinding.FragmentConfigurationSetupBinding
import com.amazon.ivs.broadcast.models.ui.PopupModel
import com.amazon.ivs.broadcast.models.ui.PopupType
import com.amazon.ivs.broadcast.ui.fragments.BaseFragment
import com.amazon.ivs.broadcast.ui.fragments.autoconfiguration.AutoConfigurationViewModel
import com.amazonaws.ivs.broadcast.BroadcastSessionTest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class ConfigurationSetupFragment : BaseFragment(R.layout.fragment_configuration_setup) {
    private val binding by viewBinding(FragmentConfigurationSetupBinding::bind)
    private val autoConfigurationViewModel by activityViewModels<AutoConfigurationViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.setupIngestServerValue.text = if (configurationViewModel.ingestServerUrl?.isBlank() == true) {
            getString(R.string.not_set)
        } else {
            configurationViewModel.ingestServerUrl
        }
        binding.setupStreamKeyValue.text = if (configurationViewModel.streamKey?.isBlank() == true) {
            getString(R.string.not_set)
        } else {
            getString(R.string.concealed_stream_key)
        }
        binding.runConfiguration.isEnabled = checkIfInputsAreSet()

        clearPopUp()
        if (autoConfigurationViewModel.rerunConfiguration) {
            binding.testProgress.progress = 0
            startTest()
            autoConfigurationViewModel.rerunConfiguration = false
        } else {
            binding.isTestActive = false
        }

        binding.setupNote.createLinks(Pair(getString(R.string.link_amazon_channel)) {
            configurationViewModel.webViewUrl = AMAZON_IVS_URL
            openFragment(R.id.navigation_web_view)
        })

        binding.skipConfiguration.setOnClickListener {
            openFragment(R.id.navigation_main)
        }

        binding.ingestServerContainer.setOnClickListener {
            binding.root.showInputDialog(
                resources.getString(R.string.ingest_server),
                inputHint = resources.getString(R.string.endpoint_url_base),
                inputText = configurationViewModel.ingestServerUrl
            ) { url ->
                configurationViewModel.ingestServerUrl = url
                binding.setupIngestServerValue.text = url.ifBlank { getString(R.string.not_set) }
                binding.runConfiguration.isEnabled = checkIfInputsAreSet()
            }
        }

        binding.streamKeyContainer.setOnClickListener {
            binding.root.showInputDialog(
                resources.getString(R.string.stream_key),
                resources.getString(R.string.stream_key_input_note),
                resources.getString(R.string.stream_key),
                configurationViewModel.streamKey
            ) { streamKey ->
                configurationViewModel.streamKey = streamKey
                binding.setupStreamKeyValue.text = if (streamKey == getString(R.string.not_set)) getString(R.string.not_set) else resources.getString(R.string.concealed_stream_key)
                binding.runConfiguration.isEnabled = checkIfInputsAreSet()
            }
        }

        binding.runConfiguration.setOnClickListener {
            autoConfigurationViewModel.isRanFromSettingsView = false
            autoConfigurationViewModel.rerunConfiguration = true
            startTest()
        }

        binding.cancelConfiguration.setOnClickListener {
            autoConfigurationViewModel.stopTest()
            if (autoConfigurationViewModel.isRanFromSettingsView) {
                openFragment(R.id.navigation_settings)
            } else {
                binding.isTestActive = false
                binding.testProgress.progress = 0
                clearPopUp()
            }
        }

        binding.popupContainer.setOnClickListener {
            clearPopUp()
        }

        autoConfigurationViewModel.testProgress.observeConsumable(viewLifecycleOwner) { progress ->
            binding.testProgress.progress = progress
        }

        autoConfigurationViewModel.onWarningReceived.observeConsumable(viewLifecycleOwner) {
            if (binding.isTestActive == true) {
                showPopup(
                    PopupModel(
                        getString(R.string.network_issue),
                        getString(R.string.long_test_message),
                        PopupType.WARNING
                    )
                )
            }
        }

        autoConfigurationViewModel.testStatus.observeConsumable(viewLifecycleOwner) { status ->
            when (status) {
                BroadcastSessionTest.Status.SUCCESS -> {
                    openFragment(R.id.navigation_configuration_summary)
                    autoConfigurationViewModel.stopTimer()
                    clearPopUp()
                }
                BroadcastSessionTest.Status.ERROR -> {
                    binding.isTestActive = false
                    autoConfigurationViewModel.stopTimer()
                    showPopup(
                        PopupModel(
                            getString(R.string.error),
                            getString(R.string.connection_error),
                            PopupType.WARNING
                        )
                    )
                }
                BroadcastSessionTest.Status.CONNECTING -> { /* Ignored */ }
                BroadcastSessionTest.Status.TESTING -> { /* Ignored */ }
            }
        }
    }

    private fun startTest() {
        autoConfigurationViewModel.shouldTestContinue = true
        binding.isTestActive = true
        clearPopUp()
        autoConfigurationViewModel.startTest(
            configurationViewModel.ingestServerUrl,
            configurationViewModel.streamKey,
            requireContext()
        )
    }

    fun canGoBack(): Boolean {
        return if (binding.isTestActive == true) {
            binding.isTestActive = false
            autoConfigurationViewModel.shouldTestContinue = false
            false
        } else {
            true
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

    private fun checkIfInputsAreSet() =
        binding.setupIngestServerValue.text.toString() != getString(R.string.not_set) &&
                binding.setupStreamKeyValue.text.toString() != getString(R.string.not_set)
}
