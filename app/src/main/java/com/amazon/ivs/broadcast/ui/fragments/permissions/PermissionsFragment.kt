package com.amazon.ivs.broadcast.ui.fragments.permissions

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.common.PRIVACY_POLICY_URL
import com.amazon.ivs.broadcast.common.isPermissionGranted
import com.amazon.ivs.broadcast.common.openFragment
import com.amazon.ivs.broadcast.common.viewBinding
import com.amazon.ivs.broadcast.databinding.FragmentPermissionsBinding
import com.amazon.ivs.broadcast.ui.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PermissionsFragment : BaseFragment(R.layout.fragment_permissions) {
    private val binding by viewBinding(FragmentPermissionsBinding::bind)
    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permitted ->
        binding.cameraCheckbox.isEnabled = !permitted
        binding.cameraCheckbox.isChecked = permitted
        updateContinueButton()
    }

    private val requestMicrophone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permitted ->
        binding.microphoneCheckbox.isEnabled = !permitted
        binding.microphoneCheckbox.isChecked = permitted
        updateContinueButton()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (isPermissionGranted(Manifest.permission.CAMERA)) {
            binding.cameraCheckbox.isEnabled = false
            binding.cameraCheckbox.isChecked = true
        }
        if (isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
            binding.microphoneCheckbox.isEnabled = false
            binding.microphoneCheckbox.isChecked = true
        }

        binding.cameraCheckbox.setOnCheckedChangeListener { checkBox, isChecked ->
            if (isChecked && checkBox.isEnabled) {
                requestCamera.launch(Manifest.permission.CAMERA)
            }
        }

        binding.cameraContainer.setOnClickListener {
            binding.cameraCheckbox.isChecked = true
        }

        binding.microphoneContainer.setOnClickListener {
            binding.microphoneCheckbox.isChecked = true
        }

        binding.microphoneCheckbox.setOnCheckedChangeListener { checkBox, isChecked ->
            if (isChecked && checkBox.isEnabled) {
                requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        binding.permissionsContinue.setOnClickListener {
            openFragment(R.id.navigation_configuration_setup)
        }

        binding.permissionsPrivacyNote.setOnClickListener {
            configurationViewModel.webViewUrl = PRIVACY_POLICY_URL
            openFragment(R.id.navigation_web_view)
        }
    }

    private fun updateContinueButton() {
        binding.permissionsContinue.isEnabled =
            isPermissionGranted(Manifest.permission.CAMERA) && isPermissionGranted(Manifest.permission.RECORD_AUDIO)
    }
}
