package com.amazon.ivs.broadcast.ui.fragments.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.common.isPermissionGranted
import com.amazon.ivs.broadcast.common.openFragment
import com.amazon.ivs.broadcast.databinding.FragmentSplashBinding
import com.amazon.ivs.broadcast.ui.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SplashFragment : BaseFragment() {

    private lateinit var binding: FragmentSplashBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

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
