package com.amazon.ivs.broadcast

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.amazon.ivs.broadcast.common.LineNumberDebugTree
import com.amazon.ivs.broadcast.injection.DaggerInjectionComponent
import com.amazon.ivs.broadcast.injection.InjectionComponent
import com.amazon.ivs.broadcast.injection.InjectionModule
import timber.log.Timber

open class App : Application(), ViewModelStoreOwner {

    override fun getViewModelStore() = appViewModelStore

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(LineNumberDebugTree("Amazon_IVS_Broadcast"))
        }
        component = DaggerInjectionComponent.builder().injectionModule(InjectionModule(this)).build()
    }

    companion object {
        private val appViewModelStore: ViewModelStore by lazy {
            ViewModelStore()
        }

        lateinit var component: InjectionComponent
            private set
    }
}
