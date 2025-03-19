package com.amazon.ivs.broadcast.cache

import android.content.Context
import androidx.core.content.edit
import com.amazon.ivs.broadcast.BuildConfig
import com.amazon.ivs.broadcast.common.FRAMERATE_LOW
import com.amazon.ivs.broadcast.common.INITIAL_BPS
import com.amazon.ivs.broadcast.common.INITIAL_HEIGHT
import com.amazon.ivs.broadcast.common.INITIAL_WIDTH
import com.amazon.ivs.broadcast.models.Orientation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

const val PREFERENCES_NAME = "app_preferences"

@Singleton
class PreferenceProvider @Inject constructor(
    @ApplicationContext context: Context
) {
    var isOnboardingDone by booleanPreference()
    var orientation by intPreference(Orientation.AUTO.id)
    var targetBitrate by intPreference(INITIAL_BPS)
    var customMinBitrate by intPreference(INITIAL_BPS)
    var customMaxBitrate by intPreference(INITIAL_BPS)
    var customFrameRate by intPreference(FRAMERATE_LOW)
    var width by floatPreference(INITIAL_WIDTH)
    var height by floatPreference(INITIAL_HEIGHT)
    var autoAdjustBitrate by booleanPreference()
    var useCustomBitrateLimits by booleanPreference()
    var useCustomResolution by booleanPreference()
    var useCustomFramerate by booleanPreference()
    var defaultCameraId by stringPreference()
    var defaultCameraPosition by stringPreference()
    var serverUrl by stringPreference(BuildConfig.SERVER_URL)
    var playbackUrl by stringPreference(BuildConfig.PLAYBACK_URL)
    var streamKey by stringPreference(BuildConfig.STREAM_KEY)

    private val sharedPreferences by lazy { context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE) }

    private fun booleanPreference() = object : ReadWriteProperty<Any?, Boolean> {

        override fun getValue(thisRef: Any?, property: KProperty<*>) =
            sharedPreferences.getBoolean(property.name, false)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            sharedPreferences.edit { putBoolean(property.name, value) }
        }
    }

    private fun stringPreference(defaultValue: String? = null) = object : ReadWriteProperty<Any?, String?> {

        override fun getValue(thisRef: Any?, property: KProperty<*>) = sharedPreferences.getString(property.name, defaultValue)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
            sharedPreferences.edit { putString(property.name, value) }
        }
    }

    private fun intPreference(initValue: Int) = object : ReadWriteProperty<Any?, Int> {

        override fun getValue(thisRef: Any?, property: KProperty<*>) = sharedPreferences.getInt(property.name, initValue)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            sharedPreferences.edit { putInt(property.name, value) }
        }
    }

    private fun floatPreference(initialValue: Float) = object : ReadWriteProperty<Any?, Float> {

        override fun getValue(thisRef: Any?, property: KProperty<*>) =
            sharedPreferences.getFloat(property.name, initialValue)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
            sharedPreferences.edit { putFloat(property.name, value) }
        }
    }
}
