package com.amazon.ivs.broadcast.ui.fragments.autoconfiguration

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import com.amazon.ivs.broadcast.common.TIME_UNTIL_WARNING
import com.amazon.ivs.broadcast.common.launch
import com.amazon.ivs.broadcast.models.Recommendation
import com.amazonaws.ivs.broadcast.BroadcastConfiguration
import com.amazonaws.ivs.broadcast.BroadcastSession
import com.amazonaws.ivs.broadcast.BroadcastSessionTest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

class AutoConfigurationViewModel : ViewModel() {

    var rerunConfiguration = false
    var shouldTestContinue = true
    var isRunnedFromSettingsView = false

    private val _testStatus = Channel<BroadcastSessionTest.Status>()
    private val _onWarningReceived = Channel<Unit>()
    private val _testProgress = Channel<Int>()
    private val _onRecommendationReceived = MutableStateFlow<Recommendation?>(null)

    private var testSession: BroadcastSession? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = Runnable {
        run {
            _onWarningReceived.trySend(Unit)
        }
    }

    val testStatus = _testStatus.receiveAsFlow()
    val onWarningReceived = _onWarningReceived.receiveAsFlow()
    val testProgress = _testProgress.receiveAsFlow()
    val onRecommendationReceived = _onRecommendationReceived.asStateFlow()

    fun startTest(
        endpointUrl: String?,
        streamKey: String?,
        context: Context,
    ) = launch {
        startTimer()
        BroadcastSession(context, null, BroadcastConfiguration(), emptyArray()).apply {
            testSession = this
            recommendedVideoSettings(
                endpointUrl,
                streamKey
            ) { result ->
                launch {
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
                        _onRecommendationReceived.update { recommendation }
                        Timber.d("Result: $recommendation")
                    }

                    _testProgress.send((result.progress * 100).toInt())
                    Timber.d("Progress: ${(result.progress * 100).toInt()} ${result.exception}")
                    _testStatus.send(result.status)
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
