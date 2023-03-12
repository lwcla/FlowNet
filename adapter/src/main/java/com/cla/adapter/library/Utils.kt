package com.cla.adapter.library

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.view.View
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.min

internal val Int.dp: Int
    get() {
        val scale = Resources.getSystem().displayMetrics.density
        return (this * scale + 0.5f).toInt()
    }

var <T : View> T.triggerDelay: Long
    get() = if (getTag(R.id.click_delay_time) != null) getTag(R.id.click_delay_time) as Long else 600
    set(value) {
        setTag(R.id.click_delay_time, value)
    }

var <T : View> T.triggerLastTime: Long
    get() = if (getTag(R.id.click_last_time) != null) getTag(R.id.click_last_time) as Long else -601
    set(value) {
        setTag(R.id.click_last_time, value)
    }


fun <T : View> T.clickEnable(): Boolean {
    var flag = false
    val currentClickTime = System.currentTimeMillis()
    if (currentClickTime - triggerLastTime >= triggerDelay) {
        flag = true
    }
    triggerLastTime = currentClickTime
    return flag
}


internal fun Context.colorValue(@ColorRes colorRes: Int, percent: Float = 1f) = colorRes.run {
    kotlin.runCatching {
        val colorInt = ContextCompat.getColor(this@colorValue, this)
        val r = Color.red(colorInt)
        val g = Color.green(colorInt)
        val b = Color.blue(colorInt)
        val a = Color.alpha(colorInt)
        val alpha = min(abs((a * percent).toInt()), 255)
        Color.argb(alpha, r, g, b)
    }.getOrElse {
        this
    }
}

/***
 * 带延迟过滤的点击事件View扩展
 * @param delay Long 延迟时间，默认600毫秒
 * @param block: (T) -> Unit 函数
 * @return Unit
 */
inline fun <V : View> V.clickDebounce(time: Long = 600, crossinline block: (V) -> Unit) {
    triggerDelay = time
    setOnClickListener {
        if (clickEnable()) {
            block(it as V)
        }
    }
}

