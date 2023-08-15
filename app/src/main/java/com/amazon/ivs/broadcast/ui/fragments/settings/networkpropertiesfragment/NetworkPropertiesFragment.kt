package com.amazon.ivs.broadcast.ui.fragments.settings.networkpropertiesfragment

import android.os.Bundle
import android.view.View
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.common.*
import com.amazon.ivs.broadcast.databinding.FragmentNetworkPropertiesBinding
import com.amazon.ivs.broadcast.ui.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NetworkPropertiesFragment : BaseFragment(R.layout.fragment_network_properties) {
    private val binding by viewBinding(FragmentNetworkPropertiesBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.adjustBitrateSwitch.isChecked = configurationViewModel.autoAdjustBitrate
        binding.manualBitrateSwitch.isChecked = configurationViewModel.useCustomBitrateLimits
        binding.manualBitrateInputContainer.setVisible(configurationViewModel.useCustomBitrateLimits)

        binding.estimatedDataUseValue.text = toFormattedGbPerHour(configurationViewModel.targetBitrate)
        binding.targetBitrateValue.text = toFormattedKbps(configurationViewModel.targetBitrate)
        binding.minimumBitrateValue.text = toFormattedKbps(configurationViewModel.minimumBitrate)
        binding.maximumBitrateValue.text = toFormattedKbps(configurationViewModel.maximumBitrate)

        binding.backButton.setOnClickListener {
            openFragment(R.id.navigation_settings)
        }

        binding.adjustBitrateSwitch.setOnCheckedChangeListener { _, isChecked ->
            configurationViewModel.autoAdjustBitrate = isChecked
        }

        binding.manualBitrateSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.manualBitrateInputContainer.setVisible(isChecked)
            configurationViewModel.useCustomBitrateLimits = isChecked
        }

        binding.minimumBitrate.setOnClickListener {
            binding.root.showInputDialog(
                getString(R.string.minimum_bitrate),
                getString(
                    R.string.minimum_bitrate_description_template,
                    configurationViewModel.targetBitrate.toKbps().toString()
                ),
                getString(R.string.bitrate),
                configurationViewModel.minimumBitrate.toKbps().toString()
            ) { kbps ->
                configurationViewModel.minimumBitrate = kbps.toBps()
                binding.minimumBitrateValue.text = toFormattedKbps(kbps.toBps())
            }
        }

        binding.maximumBitrate.setOnClickListener {
            binding.root.showInputDialog(
                getString(R.string.maximum_bitrate),
                getString(
                    R.string.maximum_bitrate_description_template,
                    toFormattedKbps(configurationViewModel.targetBitrate)
                ),
                getString(R.string.bitrate),
                configurationViewModel.maximumBitrate.toKbps().toString(),
            ) { kbps ->
                configurationViewModel.maximumBitrate = kbps.toBps()
                binding.maximumBitrateValue.text = toFormattedKbps(kbps.toBps())
            }
        }

        binding.targetBitrate.setOnClickListener {
            binding.root.showInputDialog(
                getString(R.string.target_bitrate),
                getString(R.string.target_bitrate_description),
                getString(R.string.bitrate),
                configurationViewModel.targetBitrate.toKbps().toString()
            ) { kbps ->
                configurationViewModel.targetBitrate = kbps.toBps()
                binding.targetBitrateValue.text = toFormattedKbps(kbps.toBps())
                binding.estimatedDataUseValue.text = toFormattedGbPerHour(kbps.toBps())
            }
        }
    }
}
