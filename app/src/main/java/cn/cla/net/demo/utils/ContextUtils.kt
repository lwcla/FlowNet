package cn.cla.net.demo.utils

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import cn.cla.net.demo.App

fun Int.toStringValue() = App.instance.resources.getString(this)

context(Context)
fun Int.colorValue() = ContextCompat.getColor(this@Context, this)


/**
 * 不加锁的lazy
 * 加锁太多会影响性能
 */
inline fun <T> lazyNone(crossinline block: () -> T) = lazy(LazyThreadSafetyMode.NONE) { block() }

/**
 * 找到控件
 */
inline fun <reified V : View> Activity.findView(@IdRes idRes: Int) = lazyNone { findViewById<V>(idRes) }
