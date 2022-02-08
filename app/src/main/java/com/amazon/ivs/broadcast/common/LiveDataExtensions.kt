package com.amazon.ivs.broadcast.common

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData

class Consumable<out T>(private val content: T) {

    private var isConsumed = false

    val consumedValue get() = content

    fun consume(): T? {
        return if (isConsumed) {
            null
        } else {
            isConsumed = true
            content
        }
    }
}

class ConsumableLiveData<T> : MutableLiveData<Consumable<T>>() {

    val consumedValue get() = value?.consumedValue

    fun postConsumable(data: T) {
        if (data is Unit || value?.consumedValue != data) {
            postValue(Consumable(data))
        }
    }

    inline fun observeConsumable(owner: LifecycleOwner, crossinline onConsumed: (T) -> Unit) {
        observe(owner) { it?.consume()?.let(onConsumed) }
    }
}
