package com.amazon.ivs.broadcast.common

import androidx.fragment.app.Fragment
import com.amazon.ivs.broadcast.R
import java.math.BigDecimal
import java.math.RoundingMode

fun Double.formatToDecimal(decimalPointCount: Int = 2): BigDecimal =
    toBigDecimal().setScale(decimalPointCount, RoundingMode.UP)

fun Float.formatToDecimal(decimalPointCount: Int = 2): BigDecimal =
    toBigDecimal().setScale(decimalPointCount, RoundingMode.UP)

fun Fragment.formatTopBarNetwork(megaBytes: Float): String {
    val gigaBytes = megaBytes / MB_TO_GB_FACTOR
    return when {
        (megaBytes >= 0) && (megaBytes <= 9.99) -> {
            resources.getString(R.string.network_megabytes_template, megaBytes.formatToDecimal(2))
        }
        (megaBytes >= 10) && (megaBytes <= 99.9) -> {
            resources.getString(R.string.network_megabytes_template, megaBytes.formatToDecimal(1))
        }
        (megaBytes >= 100) && (megaBytes <= 999) -> {
            resources.getString(R.string.network_megabytes_template, megaBytes.formatToDecimal(0))
        }
        (megaBytes >= 1000) && (megaBytes <= 9.99 * MB_TO_GB_FACTOR) -> {
            resources.getString(R.string.network_gigabytes_template, gigaBytes.formatToDecimal(2))
        }
        (megaBytes >= 10 * MB_TO_GB_FACTOR) && (megaBytes <= 99.9 * MB_TO_GB_FACTOR) -> {
            resources.getString(R.string.network_gigabytes_template, gigaBytes.formatToDecimal(1))
        }
        else -> {
            resources.getString(R.string.network_gigabytes_template, gigaBytes.formatToDecimal(0))
        }
    }
}

fun Fragment.formatTime(timeInSeconds: Int): String {
    val hours: Int = timeInSeconds / 3600
    val minutes: Int = (timeInSeconds - (hours * 3600)) / 60
    val seconds = timeInSeconds % 60

    return if (hours > 0) {
        resources.getString(
            R.string.stream_time_hh_mm_ss,
            4.toString(),
            String.format("%02d", minutes),
            String.format("%02d", seconds)
        )
    } else {
        resources.getString(R.string.stream_time_mm_ss, minutes.toString(), String.format("%02d", seconds))
    }
}
fun Fragment.toFormattedGbPerHour(bps: Int, template: Int = R.string.gb_per_h_template) = requireContext().getString(template, (bps / BPS_TO_GBPH_FACTOR).formatToDecimal(1).toString())

fun Fragment.toFormattedKbps(bps: Int, template: Int = R.string.kbps_template): String {
    var formattedKbps = ""
    try {
        formattedKbps = requireContext().getString(template,  String.format("%,d", bps.toKbps()))

    } catch (e: NumberFormatException) {
        /* Show error to user */
    }
    return formattedKbps
}
