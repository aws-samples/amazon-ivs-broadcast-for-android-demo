package com.amazon.ivs.broadcast.injection

import com.amazon.ivs.broadcast.App
import com.amazon.ivs.broadcast.cache.PREFERENCES_NAME
import com.amazon.ivs.broadcast.cache.PreferenceProvider
import com.amazon.ivs.broadcast.cache.SecuredPreferenceProvider
import dagger.Module
import dagger.Provides

import javax.inject.Singleton

@Module
class InjectionModule(private val context: App) {

    @Singleton
    @Provides
    fun providePreferences() = PreferenceProvider(context, PREFERENCES_NAME)

    @Singleton
    @Provides
    fun provideSecuredPreferences() = SecuredPreferenceProvider(context)
}
