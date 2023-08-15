package com.amazon.ivs.broadcast.injection

import android.content.Context
import com.amazon.ivs.broadcast.cache.PREFERENCES_NAME
import com.amazon.ivs.broadcast.cache.PreferenceProvider
import com.amazon.ivs.broadcast.cache.SecuredPreferenceProvider
import com.amazon.ivs.broadcast.common.broadcast.BroadcastManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InjectionModule {
    @Singleton
    @Provides
    fun providePreferences(@ApplicationContext context: Context) =
        PreferenceProvider(context, PREFERENCES_NAME)

    @Singleton
    @Provides
    fun provideSecuredPreferences(@ApplicationContext context: Context) =
        SecuredPreferenceProvider(context)

    @Singleton
    @Provides
    fun provideBroadcastManager(@ApplicationContext context: Context) =
        BroadcastManager(context)
}
