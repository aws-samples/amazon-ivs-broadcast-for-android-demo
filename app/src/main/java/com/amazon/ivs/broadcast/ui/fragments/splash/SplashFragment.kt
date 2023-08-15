package com.amazon.ivs.broadcast.ui.fragments.splash

import android.os.Bundle
import android.view.View
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.common.isPermissionGranted
import com.amazon.ivs.broadcast.common.openFragment
import com.amazon.ivs.broadcast.common.viewBinding
import com.amazon.ivs.broadcast.databinding.FragmentSplashBinding
import com.amazon.ivs.broadcast.ui.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SplashFragment : BaseFragment(R.layout.fragment_splash) {
    private val binding by viewBinding(FragmentSplashBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.splashGetStarted.setOnClickListener {
            if (isPermissionGranted(android.Manifest.permission.RECORD_AUDIO)
                && isPermissionGranted(android.Manifest.permission.CAMERA)
            ) {
                openFragment(R.id.navigation_configuration_setup)
            } else {
                openFragment(R.id.navigation_permissions)
            }
        }
    }
}
