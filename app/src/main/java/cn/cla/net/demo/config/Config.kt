package cn.cla.net.demo.config

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import cn.cla.net.demo.R
import cn.cla.net.demo.utils.colorValue
import com.blankj.utilcode.util.SizeUtils

context(Context)
val colorC12
    get() = R.color.black.colorValue()


context(View)
val colorC12
    get() = context.run { R.color.black.colorValue() }

context(View)
val colorC1
    get() = context.run { R.color.c1.colorValue() }


val Int.dp: Int
    get() = SizeUtils.dp2px(this.toFloat())


inline fun <reified T : Activity> Activity.jumpAty() {
    startActivity(Intent(this, T::class.java))
}