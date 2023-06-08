package com.amazon.ivs.broadcast.ui.fragments

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.transition.Fade
import com.amazon.ivs.broadcast.cache.PreferenceProvider
import com.amazon.ivs.broadcast.common.ANIMATION_DURATION
import com.amazon.ivs.broadcast.ui.fragments.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
open class BaseFragment : Fragment() {

    @Inject lateinit var preferences: PreferenceProvider

    val configurationViewModel by activityViewModels<ConfigurationViewModel>()
    val viewModel by activityViewModels<MainViewModel>()
    override fun getEnterTransition() = Fade(Fade.MODE_IN).apply {
        duration = ANIMATION_DURATION
    }

    override fun getReenterTransition() = Fade(Fade.MODE_IN).apply {
        duration = ANIMATION_DURATION
    }

    override fun getExitTransition() = Fade(Fade.MODE_OUT).apply {
        duration = ANIMATION_DURATION
    }
}
