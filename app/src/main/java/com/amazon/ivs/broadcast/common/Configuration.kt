package com.amazon.ivs.broadcast.common

import com.amazon.ivs.broadcast.models.ResolutionModel

const val AMAZON_IVS_URL = "https://aws.amazon.com/ivs/"
const val PRIVACY_POLICY_URL = "https://aws.amazon.com/privacy/"

const val SLOT_SCREEN_SHARE = "screen_share_slot"
const val SLOT_DEFAULT = "camera_slot"

val slotNames = listOf(
    SLOT_SCREEN_SHARE,
    SLOT_DEFAULT
)

const val INITIAL_BPS = 1500000
const val MIN_BPS = 100000
const val MAX_BPS = 8500000
const val INITIAL_WIDTH = 720f
const val INITIAL_HEIGHT: Float = 1280f
const val INITIAL_FRAME_RATE: Int = 30

const val ANIMATION_DURATION = 250L
const val TIME_UNTIL_WARNING = 15000L
const val POPUP_DURATION = 10000L
const val DISABLE_DURATION = 1000L

const val BYTES_TO_MEGABYTES_FACTOR = 10485760
const val MB_TO_GB_FACTOR = 1024

const val BPS_TO_KBPS_FACTOR = 0.001
const val KPBS_TO_BPS_FACTOR = 1000
const val BPS_TO_GBPH_FACTOR = 2222222.2222222

const val FRAMERATE_LOW = 15
const val FRAMERATE_MIDDLE = 30
const val FRAMERATE_HIGH = 60

val RESOLUTION_HIGH = ResolutionModel(1080f, 1920f)
val RESOLUTION_MIDDLE = ResolutionModel(720f, 1080f)
val RESOLUTION_LOW = ResolutionModel(480f, 720f)
val CPU_TEMP_PATHS = listOf(
    "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
    "/sys/devices/system/cpu/cpu0/cpufreq/FakeShmoo_cpu_temp",
    "/sys/class/thermal/thermal_zone0/temp",
    "/sys/class/i2c-adapter/i2c-4/4-004c/temperature",
    "/sys/devices/platform/tegra-i2c.3/i2c-4/4-004c/temperature",
    "/sys/devices/platform/omap/omap_temp_sensor.0/temperature",
    "/sys/devices/platform/tegra_tmon/temp1_input",
    "/sys/kernel/debug/tegra_thermal/temp_tj",
    "/sys/devices/platform/s5p-tmu/temperature",
    "/sys/class/thermal/thermal_zone1/temp",
    "/sys/class/hwmon/hwmon0/device/temp1_input",
    "/sys/devices/virtual/thermal/thermal_zone1/temp",
    "/sys/devices/virtual/thermal/thermal_zone0/temp",
    "/sys/class/thermal/thermal_zone3/temp",
    "/sys/class/thermal/thermal_zone4/temp",
    "/sys/class/hwmon/hwmonX/temp1_input",
    "/sys/devices/platform/s5p-tmu/curr_temp",
    "/sys/class/thermal/thermal_zone0/temp"
)
