package cn.cla.library.net.utils

import android.content.Context
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.os.Build
import androidx.annotation.ColorRes
import androidx.core.widget.ContentLoadingProgressBar
import cn.cla.library.net.R

/**
 * 把颜色设置为c1
 */
internal fun ContentLoadingProgressBar.setC1(ctx: Context) {
    setBarColor(ctx, R.color.cla_net_c1)
}

/**
 * 把颜色设置为c1
 */
internal fun ContentLoadingProgressBar.setBarColor(ctx: Context, @ColorRes colorRes: Int) {
    //这样设置的颜色才有效
    try {
        val c = ctx.colorValue(colorRes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            indeterminateDrawable.colorFilter = BlendModeColorFilter(c, BlendMode.SRC_ATOP)
        } else {
            indeterminateDrawable.setColorFilter(c, PorterDuff.Mode.SRC_ATOP)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}