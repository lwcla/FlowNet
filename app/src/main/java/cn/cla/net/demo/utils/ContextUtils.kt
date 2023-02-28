package cn.cla.net.demo.utils

import android.app.Activity
import android.view.View
import androidx.annotation.IdRes
import cn.cla.net.demo.App

fun Int.toStringValue() = App.instance.resources.getString(this)

/**
 * 不加锁的lazy
 * 加锁太多会影响性能
 */
inline fun <T> lazyNone(crossinline block: () -> T) = lazy(LazyThreadSafetyMode.NONE) { block() }

/**
 * 找到控件
 */
inline fun <reified V : View> Activity.findView(@IdRes idRes: Int) = lazyNone { findViewById<V>(idRes) }
