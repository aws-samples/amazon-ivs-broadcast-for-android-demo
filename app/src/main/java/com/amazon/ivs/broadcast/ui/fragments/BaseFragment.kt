package com.amazon.ivs.broadcast.ui.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.transition.Fade
import com.amazon.ivs.broadcast.App
import com.amazon.ivs.broadcast.cache.PreferenceProvider
import com.amazon.ivs.broadcast.cache.SecuredPreferenceProvider
import com.amazon.ivs.broadcast.common.ANIMATION_DURATION
import com.amazon.ivs.broadcast.common.broadcast.BroadcastManager
import com.amazon.ivs.broadcast.common.lazyViewModel
import com.amazon.ivs.broadcast.ui.fragments.main.MainViewModel
import javax.inject.Inject

open class BaseFragment : Fragment() {

    @Inject lateinit var preferences: PreferenceProvider
    @Inject lateinit var securedPreferences: SecuredPreferenceProvider
    @Inject lateinit var broadcastManager: BroadcastManager

    val configurationViewModel by lazyViewModel(
        { requireActivity().application as App },
        { ConfigurationViewModel(securedPreferences, preferences) }
    )

    val viewModel by lazyViewModel(
        { requireActivity().application as App },
        { MainViewModel(configurationViewModel, broadcastManager.apply { init(configurationViewModel) }) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.component.inject(this)
    }

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
