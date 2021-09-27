package com.amazon.ivs.broadcast.common

import timber.log.Timber

class LineNumberDebugTree(private val tag: String) : Timber.DebugTree() {

    override fun createStackElementTag(element: StackTraceElement) =
        "$tag: (${element.fileName}:${element.lineNumber}) #${element.methodName} "
}
