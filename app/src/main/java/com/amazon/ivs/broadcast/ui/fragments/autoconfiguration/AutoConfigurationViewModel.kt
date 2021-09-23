package com.amazon.ivs.broadcast.ui.fragments.autoconfiguration

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import com.amazon.ivs.broadcast.common.ConsumableLiveData
import com.amazon.ivs.broadcast.common.TIME_UNTIL_WARNING
import com.amazon.ivs.broadcast.common.launchMain
import com.amazon.ivs.broadcast.models.Recommendation
import com.amazonaws.ivs.broadcast.BroadcastConfiguration
import com.amazonaws.ivs.broadcast.BroadcastSession
import com.amazonaws.ivs.broadcast.BroadcastSessionTest
import timber.log.Timber

class AutoConfigurationViewModel : ViewModel() {

    var rerunConfiguration = false
    var shouldTestContinue = true
    var isRunnedFromSettingsView = false

    val testStatus = ConsumableLiveData<BroadcastSessionTest.Status>()
    val onWarningReceived = ConsumableLiveData<Unit>()
    val testProgress = ConsumableLiveData<Int>()
    val onRecommendationReceived = ConsumableLiveData<Recommendation>()

    private var testSession: BroadcastSession? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = Runnable {
        run {
            onWarningReceived.postConsumable(Unit)
        }
    }

    fun startTest(
        endpointUrl: String?,
        streamKey: String?,
        context: Context,
    ) = launchMain {
        startTimer()
        BroadcastSession(context, null, BroadcastConfiguration(), emptyArray()).apply {
            testSession = this
            recommendedVideoSettings(
                endpointUrl,
                streamKey
            ) { result ->
                launchMain {
                    if (!shouldTestContinue) stopTest()
                    result.recommendations.firstOrNull()?.run {
                        val recommendation = Recommendation(
                            size.x,
                            size.y,
                            targetFramerate,
                            minBitrate,
                            initialBitrate,
                            maxBitrate
                        )
                        onRecommendationReceived.postConsumable(recommendation)
                        Timber.d("Result: $recommendation")
                    }

                    testProgress.postConsumable((result.progress * 100).toInt())
                    Timber.d("Progress: ${(result.progress * 100).toInt()} ${result.exception}")
                    testStatus.postConsumable(result.status)
                }
            }
        }
    }

    private fun startTimer() {
        timerHandler.postDelayed(timerRunnable, TIME_UNTIL_WARNING)
    }

    fun stopTimer() {
        timerHandler.removeCallbacks(timerRunnable)
    }

    fun stopTest() {
        testSession?.stop()
        release()
        stopTimer()
    }

    fun release() {
        testSession?.release()
        testSession = null
    }
}
