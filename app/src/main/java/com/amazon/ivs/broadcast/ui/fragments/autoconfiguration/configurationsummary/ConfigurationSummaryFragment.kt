package com.amazon.ivs.broadcast.ui.fragments.autoconfiguration.configurationsummary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.common.openFragment
import com.amazon.ivs.broadcast.common.toFormattedGbPerHour
import com.amazon.ivs.broadcast.common.toFormattedKbps
import com.amazon.ivs.broadcast.databinding.FragmentConfigurationSummaryBinding
import com.amazon.ivs.broadcast.ui.fragments.BaseFragment
import com.amazon.ivs.broadcast.ui.fragments.autoconfiguration.AutoConfigurationViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ConfigurationSummaryFragment : BaseFragment() {

    private lateinit var binding: FragmentConfigurationSummaryBinding
    private val autoConfigurationViewModel by activityViewModels<AutoConfigurationViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentConfigurationSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        autoConfigurationViewModel.onRecommendationReceived.value?.run {
            configurationViewModel.recommendation = this

            binding.summaryBitrateValue.text = toFormattedKbps(targetBitrate)
            binding.summaryDataUsageValue.text = toFormattedGbPerHour(targetBitrate)
            binding.summaryQualityValue.text = getString(
                    R.string.quality_template,
                    (if (width > height) height else width).toInt(),
                    frameRate
            )
        }

        binding.continueToApp.setOnClickListener {
            autoConfigurationViewModel.release()
            if (autoConfigurationViewModel.isRunnedFromSettingsView) openFragment(R.id.navigation_settings) else openFragment(R.id.navigation_main)
        }

        binding.rerunConfiguration.setOnClickListener {
            autoConfigurationViewModel.rerunConfiguration = true
            openFragment(R.id.navigation_configuration_setup)
        }
    }
}
