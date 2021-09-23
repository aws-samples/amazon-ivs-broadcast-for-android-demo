package com.amazon.ivs.broadcast.ui.fragments.autoconfiguration.configurationsetup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.amazon.ivs.broadcast.App
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.common.*
import com.amazon.ivs.broadcast.databinding.FragmentConfigurationSetupBinding
import com.amazon.ivs.broadcast.models.ui.PopupModel
import com.amazon.ivs.broadcast.models.ui.PopupType
import com.amazon.ivs.broadcast.ui.fragments.BaseFragment
import com.amazon.ivs.broadcast.ui.fragments.autoconfiguration.AutoConfigurationViewModel
import com.amazonaws.ivs.broadcast.BroadcastSessionTest
import kotlinx.coroutines.delay

class ConfigurationSetupFragment : BaseFragment() {

    private lateinit var binding: FragmentConfigurationSetupBinding
    private val viewModel by lazyViewModel(
        { requireActivity().application as App },
        { AutoConfigurationViewModel() }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentConfigurationSetupBinding.inflate(inflater, container, false)
        App.component.inject(this)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.setupIngestServerValue.text =
            if (configurationViewModel.ingestServerUrl?.isBlank() == true) getString(R.string.not_set) else configurationViewModel.ingestServerUrl
        binding.setupStreamKeyValue.text =
            if (configurationViewModel.streamKey?.isBlank() == true) getString(R.string.not_set) else getString(R.string.concealed_stream_key)
        binding.runConfiguration.isEnabled = checkIfInputsAreSet()

        clearPopUp()
        if (viewModel.rerunConfiguration) {
            binding.testProgress.progress = 0
            startTest()
            viewModel.rerunConfiguration = false
        } else {
            binding.isTestActive = false
        }

        binding.setupNote.createLinks(Pair(getString(R.string.link_amazon_channel), {
            configurationViewModel.webViewUrl = AMAZON_IVS_URL
            openFragment(R.id.navigation_web_view)
        }))

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
                binding.setupIngestServerValue.text = if (url.isBlank()) getString(R.string.not_set) else url
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
            viewModel.isRunnedFromSettingsView = false
            viewModel.rerunConfiguration = true
            startTest()
        }

        binding.cancelConfiguration.setOnClickListener {
            viewModel.stopTest()
            if (viewModel.isRunnedFromSettingsView) {
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

        viewModel.testProgress.observeConsumable(viewLifecycleOwner) { progress ->
            binding.testProgress.progress = progress
        }

        viewModel.onWarningReceived.observeConsumable(viewLifecycleOwner) {
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

        viewModel.testStatus.observeConsumable(viewLifecycleOwner) { status ->
            when (status) {
                BroadcastSessionTest.Status.SUCCESS -> {
                    openFragment(R.id.navigation_configuration_summary)
                    viewModel.stopTimer()
                    clearPopUp()
                }
                BroadcastSessionTest.Status.ERROR -> {
                    binding.isTestActive = false
                    viewModel.stopTimer()
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
        viewModel.shouldTestContinue = true
        binding.isTestActive = true
        clearPopUp()
        viewModel.startTest(
            configurationViewModel.ingestServerUrl,
            configurationViewModel.streamKey,
            requireContext()
        )
    }

    fun canGoBack(): Boolean {
        return if (binding.isTestActive == true) {
            binding.isTestActive = false
            viewModel.shouldTestContinue = false
            false
        } else {
            true
        }
    }

    private fun showPopup(popupUpdateModel: PopupModel, setTimer: Boolean = true) {
        binding.popupUpdate = popupUpdateModel
        binding.popupContainer.setVisible()
        if (setTimer) {
            launchMain {
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
