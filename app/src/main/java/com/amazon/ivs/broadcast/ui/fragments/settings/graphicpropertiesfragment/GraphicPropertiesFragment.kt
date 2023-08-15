package com.amazon.ivs.broadcast.ui.fragments.settings.graphicpropertiesfragment

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.common.*
import com.amazon.ivs.broadcast.databinding.FragmentGraphicPropertiesBinding
import com.amazon.ivs.broadcast.models.ui.PopupModel
import com.amazon.ivs.broadcast.models.ui.PopupType
import com.amazon.ivs.broadcast.ui.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class GraphicPropertiesFragment : BaseFragment(R.layout.fragment_graphic_properties) {
    private val binding by viewBinding(FragmentGraphicPropertiesBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchFramerateOptionVisibility(configurationViewModel.useCustomFramerate)
        switchResolutionOptionVisibility(configurationViewModel.useCustomResolution)
        binding.customResolutionSwitch.isChecked = configurationViewModel.useCustomResolution
        binding.customFramerateSwitch.isChecked = configurationViewModel.useCustomFramerate
        binding.resolutionOptions.check(getResolutionOption())
        binding.framerateOptions.check(getFramerateOption())
        binding.customFramerate.text = getString(R.string.framerate_template, configurationViewModel.framerate)
        updateOrientationContainer()
        updateResolutionValue()

        binding.backButton.setOnClickListener {
            openFragment(R.id.navigation_settings)
        }

        binding.orientation.setOnClickListener {
            binding.root.showOrientationOptions(
                getString(R.string.orientation),
                configurationViewModel.orientationId.getOrientation()
            ) { orientation ->
                configurationViewModel.orientationId = orientation.id
                binding.orientationValue.text = orientation.value
                updateResolutionValue()
            }
        }

        binding.customResolution.setOnClickListener {
            showResolutionDialog()
        }

        binding.customFramerate.setOnClickListener {
            showFrameRateDialog()
        }

        binding.customResolutionSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                showResolutionDialog(binding.customResolutionSwitch)
            } else {
                configurationViewModel.useCustomResolution = false
                configurationViewModel.resolution.initialWidth = configurationViewModel.recommendation.width
                configurationViewModel.resolution.initialHeight = configurationViewModel.recommendation.height
                binding.resolutionOptions.check(getResolutionOption())
                switchResolutionOptionVisibility(false)
            }

            updateOrientationContainer()
        }

        binding.customFramerateSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                showFrameRateDialog(binding.customFramerateSwitch)
            } else {
                configurationViewModel.useCustomFramerate = false
                configurationViewModel.framerate = configurationViewModel.recommendation.frameRate
                binding.framerateOptions.check(getFramerateOption())
                switchFramerateOptionVisibility(false)
            }
        }

        binding.resolutionOptions.setOnCheckedChangeListener { _, checkedId ->
            val resolution = when (checkedId) {
                binding.optionHighestResolution.id -> RESOLUTION_HIGH
                binding.optionMiddleResolution.id -> RESOLUTION_MIDDLE
                else -> RESOLUTION_LOW
            }
            configurationViewModel.resolution = resolution
            configurationViewModel.recommendation.height = resolution.height
            configurationViewModel.recommendation.width = resolution.width
        }

        binding.framerateOptions.setOnCheckedChangeListener { _, checkedId ->
            val frameRate = when (checkedId) {
                binding.optionHighestFramerate.id -> FRAMERATE_HIGH
                binding.optionMiddleFramerate.id -> FRAMERATE_MIDDLE
                else -> FRAMERATE_LOW
            }
            configurationViewModel.recommendation.frameRate = frameRate
            configurationViewModel.framerate = frameRate
        }

        binding.popupContainer.setOnClickListener {
            clearPopup()
        }
    }

    private fun updateResolutionValue() {
        binding.customResolution.text = getString(
            R.string.resolution_template,
            configurationViewModel.resolution.width.toInt(),
            configurationViewModel.resolution.height.toInt()
        )
    }

    private fun getResolutionOption(): Int = with(configurationViewModel.resolution) {
        return when (shortestSide) {
            RESOLUTION_MIDDLE.shortestSide -> binding.optionMiddleResolution.id
            RESOLUTION_HIGH.shortestSide -> binding.optionHighestResolution.id
            else -> binding.optionLowResolution.id
        }
    }

    private fun getFramerateOption(): Int {
        return when (configurationViewModel.framerate) {
            FRAMERATE_HIGH -> binding.optionHighestFramerate.id
            FRAMERATE_MIDDLE -> binding.optionMiddleFramerate.id
            else -> binding.optionLowFramerate.id
        }
    }

    private fun switchResolutionOptionVisibility(customVisible: Boolean = true) {
        binding.resolutionOptions.setVisible(!customVisible)
        binding.customResolution.setVisible(customVisible)
        val firstSeparatorParams = binding.resolutionFirstSeparator.root.layoutParams as ConstraintLayout.LayoutParams
        val secondSeparatorParams = binding.resolutionSecondSeparator.root.layoutParams as ConstraintLayout.LayoutParams

        if (customVisible) {
            firstSeparatorParams.bottomToTop = binding.customResolution.id
            secondSeparatorParams.topToBottom = binding.customResolution.id
        } else {
            firstSeparatorParams.bottomToTop = binding.resolutionOptions.id
            secondSeparatorParams.topToBottom = binding.resolutionOptions.id
        }
        binding.resolutionFirstSeparator.root.layoutParams = firstSeparatorParams
        binding.resolutionSecondSeparator.root.layoutParams = secondSeparatorParams
    }

    private fun switchFramerateOptionVisibility(customVisible: Boolean = true) {
        binding.framerateOptions.setVisible(!customVisible)
        binding.customFramerate.setVisible(customVisible)
        val firstSeparatorParams = binding.framerateFirstSeparator.root.layoutParams as ConstraintLayout.LayoutParams
        val secondSeparatorParams = binding.framerateSecondSeparator.root.layoutParams as ConstraintLayout.LayoutParams

        if (customVisible) {
            firstSeparatorParams.bottomToTop = binding.customFramerate.id
            secondSeparatorParams.topToBottom = binding.customFramerate.id
        } else {
            firstSeparatorParams.bottomToTop = binding.framerateOptions.id
            secondSeparatorParams.topToBottom = binding.framerateOptions.id
        }
        binding.framerateFirstSeparator.root.layoutParams = firstSeparatorParams
        binding.framerateSecondSeparator.root.layoutParams = secondSeparatorParams
    }

    private fun showFrameRateDialog(switch: SwitchCompat? = null) {
        binding.root.showFramerateDialog(
            configurationViewModel.framerate.toString(),
            switch
        ) { framerate ->
            binding.customFramerate.text = getString(R.string.fps_template, framerate)
            switchFramerateOptionVisibility()
            configurationViewModel.framerate = framerate
            configurationViewModel.useCustomFramerate = true
            clearPopup()
        }
    }

    private fun showResolutionDialog(switch: SwitchCompat? = null) {
        binding.root.showResolutionDialog(
            getString(
                R.string.resolution_template,
                configurationViewModel.resolution.width.toInt(),
                configurationViewModel.resolution.height.toInt()
            ),
            switch
        ) { customResolution ->
            configurationViewModel.useCustomResolution = true
            configurationViewModel.resolution = customResolution
            updateOrientationContainer()
            updateResolutionValue()
            switchResolutionOptionVisibility()
            clearPopup()
        }
    }

    private fun updateOrientationContainer() {
        Timber.d(configurationViewModel.orientationId.getOrientation().name)
        binding.orientationValue.text = configurationViewModel.orientationId.getOrientation().name
        binding.orientation.isEnabled = !configurationViewModel.useCustomResolution
    }

    private fun clearPopup() {
        binding.popupUpdate = PopupModel(type = PopupType.NONE)
    }
}
