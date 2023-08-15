package com.amazon.ivs.broadcast.common

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.TrafficStats
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.models.Orientation
import com.amazon.ivs.broadcast.models.Orientation.*
import com.amazonaws.ivs.broadcast.BroadcastConfiguration
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.RandomAccessFile

fun AppCompatActivity.openFragment(id: Int) {
    findNavController(R.id.nav_host_fragment).navigate(id)
}

fun Fragment.openFragment(id: Int) {
    (this.activity as? AppCompatActivity)?.openFragment(id)
}

fun CoordinatorLayout.showSnackBar(message: String, onClicked: () -> Unit): Snackbar {
    val snackBar = Snackbar.make(this, message, Snackbar.LENGTH_INDEFINITE)
    snackBar.setAction(context.getString(R.string.retry)) {
        snackBar.dismiss()
        onClicked()
    }.show()
    return snackBar
}

fun AppCompatActivity.getCurrentFragment() =
    supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.fragments?.firstOrNull()

fun Fragment.isPermissionGranted(permissionId: String) =
    requireContext().checkCallingOrSelfPermission(permissionId) == PackageManager.PERMISSION_GRANTED

fun View.setVisible(isVisible: Boolean = true, hideOption: Int = View.GONE) {
    visibility = if (isVisible) View.VISIBLE else hideOption
}

fun View.onDrawn(onDrawn: () -> Unit) {
    invalidate()
    requestLayout()
    doOnLayout { onDrawn() }
}

fun ConstraintLayout.LayoutParams.clearAllAnchors() {
    startToStart = ConstraintLayout.LayoutParams.UNSET
    startToEnd = ConstraintLayout.LayoutParams.UNSET
    topToTop = ConstraintLayout.LayoutParams.UNSET
    topToBottom = ConstraintLayout.LayoutParams.UNSET
    endToEnd = ConstraintLayout.LayoutParams.UNSET
    endToStart = ConstraintLayout.LayoutParams.UNSET
    bottomToBottom = ConstraintLayout.LayoutParams.UNSET
    bottomToTop = ConstraintLayout.LayoutParams.UNSET
    matchConstraintPercentHeight = 1f
    matchConstraintPercentWidth = 1f
    matchConstraintDefaultHeight = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD
    matchConstraintDefaultWidth = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD
}

fun TextView.createLinks(vararg links: Pair<String, () -> Unit>) {
    val spannableString = SpannableString(this.text)
    var startIndexOfLink = -1
    for (link in links) {
        val clickableSpan = object : ClickableSpan() {
            override fun updateDrawState(textPaint: TextPaint) {
                textPaint.color = textPaint.linkColor
                textPaint.isUnderlineText = false
            }

            override fun onClick(view: View) {
                Selection.setSelection((view as TextView).text as Spannable, 0)
                view.invalidate()
                link.second()
            }
        }
        startIndexOfLink = text.toString().indexOf(link.first, startIndexOfLink + 1)
        if (startIndexOfLink < 0) continue
        spannableString.setSpan(
            clickableSpan, startIndexOfLink, startIndexOfLink + link.first.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    movementMethod = LinkMovementMethod.getInstance()
    setText(spannableString, TextView.BufferType.SPANNABLE)
}

fun Context.getCpuTemperature(): String {
    var temp = 0f
    var count = 0
    CPU_TEMP_PATHS.forEach { file ->
        try {
            val reader = RandomAccessFile(file, "r")
            temp += reader.readLine().toFloat()
            count++
        } catch (e: Exception) {
            /* Ignored */
        }
    }
    val averageTemp = (temp / count / 1000.0f).toInt()
    return if (averageTemp <= 0) resources.getString(R.string.not_available_short) else resources.getString(
        R.string.cpu_temp_template,
        (averageTemp).toString()
    )
}

fun Context.getUsedMemory(): String {
    val memoryInfo = ActivityManager.MemoryInfo()
    (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memoryInfo)
    val usedMegaBytes = (memoryInfo.totalMem - memoryInfo.availMem) / BYTES_TO_MEGABYTES_FACTOR
    return resources.getString(R.string.used_memory_template, usedMegaBytes.toInt().toString())
}

fun BottomSheetBehavior<View>.setCollapsed() = run { state = BottomSheetBehavior.STATE_COLLAPSED }

fun Fragment.copyToClipBoard(text: String) {
    val clipBoard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText("json", text)
    clipBoard.setPrimaryClip(clipData)
}

fun getSessionUsedBytes(startBytes: Float) =
    ((TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()) - startBytes)

fun Int.toKbps() = (this * BPS_TO_KBPS_FACTOR).toInt()

fun Float.toBps() = this * KPBS_TO_BPS_FACTOR

fun String.toBps(): Int {
    var bps = INITIAL_BPS
    try {
        bps = toFloat().toBps().toInt().takeIf { it in MIN_BPS .. MAX_BPS } ?: MAX_BPS
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse KBPS")
    }
    return bps
}

fun View.showSoftKeyboard() {
    val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

fun View.disableAndEnable(millis: Long = DISABLE_DURATION) = launchMain {
    isEnabled = false
    delay(millis)
    isEnabled = true
}

fun Context.isViewLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

fun Int.getOrientation(): Orientation {
    return when (this) {
        AUTO.id -> AUTO
        LANDSCAPE.id -> LANDSCAPE
        PORTRAIT.id -> PORTRAIT
        else -> SQUARE
    }
}

fun Activity.startShareIntent(url: String) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, url)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    startActivity(shareIntent)
}

fun BroadcastConfiguration.asString() = "Broadcast configuration:\n" +
        "Video.initialBitrate: ${video.initialBitrate}\n" +
        "Video.minBitrate: ${video.minBitrate}\n" +
        "Video.maxBitrate: ${video.maxBitrate}\n" +
        "Video.isUseAutoBitrate: ${video.isUseAutoBitrate}\n" +
        "Video.isUseBFrames: ${video.isUseBFrames}\n" +
        "Video.keyframeInterval: ${video.keyframeInterval}\n" +
        "Video.size: (${video.size.x}, ${video.size.x})\n" +
        "Video.targetFramerate: ${video.targetFramerate}\n" +
        "Mixer.slots.size: ${mixer.slots.size}\n"
