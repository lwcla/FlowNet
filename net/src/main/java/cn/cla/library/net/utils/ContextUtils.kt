package cn.cla.library.net.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlin.math.abs
import kotlin.math.min


internal val Context.lifeCycle get() = lifeCycleOwner?.lifecycle

val Context.lifeCycleScope get() = lifeCycleOwner?.lifecycleScope

internal val Context.lifeCycleOwner
    get() = if (this is Fragment && this !is DialogFragment) {
        //Fragment 应该始终使用 viewLifecycleOwner 去触发 UI 更新. 但是这不适用于 DialogFragment. 因为它可能没有 View. 对于 DialogFragment. 你应该使用 lifecycleOwner
        viewLifecycleOwner
    } else {
        (this as? LifecycleOwner?)
    }

/**
 * 转换颜色值
 */
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

internal fun Context.drawableValue(
    @DrawableRes res: Int?,
    @ColorRes colorRes: Int? = null,
    @ColorInt color: Int? = null
) = res?.run {

    val ctx = this@drawableValue

    try {
        val drawable = ContextCompat.getDrawable(ctx, res)
        drawable?.also {
            ctx.changeSvgColor(drawable, colorRes = colorRes)
            ctx.changeSvgColor(drawable, color = color)
        }
    } catch (e: Exception) {
        null
    }
}

internal fun Context.changeSvgColor(
    drawable: Drawable?,
    @ColorRes colorRes: Int? = null,
    @ColorInt color: Int? = null
) = drawable?.apply {
    val ctx = this@changeSvgColor
    try {
        colorRes?.let { DrawableCompat.setTint(mutate(), ContextCompat.getColor(ctx, it)) }
        color?.let { DrawableCompat.setTint(mutate(), it) }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * 关闭软件盘
 */
internal fun FragmentActivity.closeInput() {
    closeInput(window.peekDecorView())
}

/**
 * 关闭软件盘
 */
internal fun FragmentActivity.closeInput(view: View?) {

    if (view == null) {
        return
    }

    try {
        val inputManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(view.windowToken, 0)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
