package com.amazon.ivs.broadcast.ui.activities

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.amazon.ivs.broadcast.App
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.cache.PreferenceProvider
import com.amazon.ivs.broadcast.cache.SecuredPreferenceProvider
import com.amazon.ivs.broadcast.common.broadcast.BroadcastManager
import com.amazon.ivs.broadcast.common.getCurrentFragment
import com.amazon.ivs.broadcast.common.lazyViewModel
import com.amazon.ivs.broadcast.common.openFragment
import com.amazon.ivs.broadcast.databinding.ActivityMainBinding
import com.amazon.ivs.broadcast.ui.fragments.ConfigurationViewModel
import com.amazon.ivs.broadcast.ui.fragments.autoconfiguration.configurationsetup.ConfigurationSetupFragment
import com.amazon.ivs.broadcast.ui.fragments.autoconfiguration.configurationsummary.ConfigurationSummaryFragment
import com.amazon.ivs.broadcast.ui.fragments.main.MainFragment
import com.amazon.ivs.broadcast.ui.fragments.main.MainViewModel
import com.amazon.ivs.broadcast.ui.fragments.settings.graphicpropertiesfragment.GraphicPropertiesFragment
import com.amazon.ivs.broadcast.ui.fragments.settings.networkpropertiesfragment.NetworkPropertiesFragment
import com.amazon.ivs.broadcast.ui.fragments.settings.settingsfragment.SettingsFragment
import com.amazon.ivs.broadcast.ui.fragments.splash.SplashFragment
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    @Inject lateinit var preferences: PreferenceProvider
    @Inject lateinit var securedPreferences: SecuredPreferenceProvider
    @Inject lateinit var broadcastManager: BroadcastManager

    private lateinit var binding: ActivityMainBinding

    val configurationViewModel by lazyViewModel(
        { application as App },
        { ConfigurationViewModel(securedPreferences, preferences) }
    )

    private val mainViewModel by lazyViewModel(
        { application as App },
        { MainViewModel(configurationViewModel, broadcastManager) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.component.inject(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (preferences.isOnboardingDone) {
            openFragment(R.id.navigation_main)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                getCurrentFragment()?.let { currentFragment ->
                    when (currentFragment) {
                        is SplashFragment -> finish()
                        is ConfigurationSetupFragment -> if (currentFragment.canGoBack()) openFragment(R.id.navigation_splash) else Unit
                        is ConfigurationSummaryFragment -> openFragment(R.id.navigation_configuration_setup)
                        is MainFragment -> if (currentFragment.onBackPressed()) finish() else Unit
                        is SettingsFragment -> openFragment(R.id.navigation_main)
                        is NetworkPropertiesFragment -> openFragment(R.id.navigation_settings)
                        is GraphicPropertiesFragment -> openFragment(R.id.navigation_settings)
                        else -> findNavController(R.id.nav_host_fragment).navigateUp()
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        mainViewModel.resetSession()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configurationViewModel.isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
}