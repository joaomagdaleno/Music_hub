package com.joaomagdaleno.music_hub.utils.ui

import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class AutoClearedValue<T : Any>(val fragment: Fragment) : ReadWriteProperty<Fragment, T> {
    private var _value: T? = null

    init {
        addOnDestroyObserver(fragment) { _value = null }
    }

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        return _value ?: throw IllegalStateException(
            "should never call auto-cleared-value get when it might not be available"
        )
    }

    override fun setValue(thisRef: Fragment, property: KProperty<*>, value: T) {
        _value = value
    }

    class Nullable<T : Any>(val fragment: Fragment) : ReadWriteProperty<Fragment, T?> {
        private var _value: T? = null

        init {
            addOnDestroyObserver(fragment) { _value = null }
        }

        override fun getValue(thisRef: Fragment, property: KProperty<*>) = _value

        override fun setValue(thisRef: Fragment, property: KProperty<*>, value: T?) {
            _value = value
        }

    }

    companion object {
        fun addOnDestroyObserver(fragment: Fragment, onDestroy: () -> Unit) {
            fragment.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onCreate(owner: LifecycleOwner) {
                    fragment.viewLifecycleOwnerLiveData.observe(fragment) {
                        it?.lifecycle?.addObserver(
                            object : DefaultLifecycleObserver {
                                override fun onDestroy(owner: LifecycleOwner) {
                                    onDestroy()
                                }
                            }
                        )
                    }
                }
            })
        }

        fun <T : Any> autoCleared(fragment: Fragment) = AutoClearedValue<T>(fragment)
        fun <T : Any> autoClearedNullable(fragment: Fragment) = Nullable<T>(fragment)
    }
}