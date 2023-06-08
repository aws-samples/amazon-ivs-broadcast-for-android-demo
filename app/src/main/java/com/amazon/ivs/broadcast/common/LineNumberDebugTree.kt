package com.amazon.ivs.broadcast.common

import timber.log.Timber

private const val TIMBER_TAG = "Amazon_IVS_Broadcast"

class LineNumberDebugTree : Timber.DebugTree() {

    override fun createStackElementTag(element: StackTraceElement) =
        "$TIMBER_TAG: (${element.fileName}:${element.lineNumber}) #${element.methodName} "
}
