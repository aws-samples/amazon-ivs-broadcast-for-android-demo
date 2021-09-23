package com.amazon.ivs.broadcast.common

import kotlinx.coroutines.*
import timber.log.Timber

private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

fun launchIO(block: suspend CoroutineScope.() -> Unit) = ioScope.launch(
    context = CoroutineExceptionHandler { _, e -> Timber.d(e, "Coroutine failed ${e.localizedMessage}") },
    block = block
)

fun launchMain(block: suspend CoroutineScope.() -> Unit) = mainScope.launch(
    context = CoroutineExceptionHandler { _, e ->
        Timber.w(e, "Coroutine failed: ${e.localizedMessage}")
    },
    block = block
)
