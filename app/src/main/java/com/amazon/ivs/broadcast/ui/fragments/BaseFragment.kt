package com.amazon.ivs.broadcast.ui.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.transition.Fade
import com.amazon.ivs.broadcast.App
import com.amazon.ivs.broadcast.cache.PreferenceProvider
import com.amazon.ivs.broadcast.cache.SecuredPreferenceProvider
import com.amazon.ivs.broadcast.common.ANIMATION_DURATION
import com.amazon.ivs.broadcast.common.lazyViewModel
import javax.inject.Inject

open class BaseFragment : Fragment() {

    @Inject lateinit var preferences: PreferenceProvider
    @Inject lateinit var securedPreferences: SecuredPreferenceProvider

    val configurationViewModel by lazyViewModel(
        { requireActivity().application as App },
        { ConfigurationViewModel(securedPreferences, preferences) }
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
