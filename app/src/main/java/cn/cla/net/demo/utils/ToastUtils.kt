package cn.cla.net.demo

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Toast
import androidx.annotation.StringRes
import java.lang.ref.WeakReference

private val toastHandler by lazy { Handler(Looper.getMainLooper()) }
private val tagMap = mutableMapOf<String, WeakReference<Toast>>()

private fun getMethodName(): String {
    val trace = Thread.currentThread().stackTrace.find {
        val info = it.toString()
        !it.isNativeMethod && !info.startsWith("java") && it.methodName != "getMethodName" && it.fileName != "ToastUtils.kt"
    }
    return trace.toString()
}

/**
 * 使用同一个tag的toast不会重复弹出来
 */
fun String?.showToast(tag: String? = getMethodName(), duration: Int = Toast.LENGTH_SHORT) {
    App.instance.toast(this, tag, duration)
}

/**
 * 使用同一个tag的toast不会重复弹出来
 */
fun Int.showToast(tag: String? = getMethodName(), duration: Int = Toast.LENGTH_SHORT) {
    App.instance.toast(this, tag, duration)
}

/**
 * 使用同一个tag的toast不会重复弹出来
 */
fun Context?.toast(
    @StringRes msgRes: Int?,
    tag: String? = null,
    duration: Int = Toast.LENGTH_SHORT
) {

    if (this == null || msgRes == null) {
        return
    }

    val msg = try {
        this.getString(msgRes)
    } catch (e: Exception) {
        null
    }

    if (msg.isNullOrEmpty()) {
        return
    }

    toast(msg, tag, duration)
}

/**
 * 使用同一个tag的toast不会重复弹出来
 * @param msg 需要提示的信息
 * @param tag 用来从toastMap获取toast的key，默认为当前的类名
 * @param duration toast显示的时长
 * @param time 是否需要增加时间限制，10s秒钟只显示一次
 */
@SuppressLint("ShowToast")
fun Context?.toast(
    msg: String?,
    tag: String? = null,
    duration: Int = Toast.LENGTH_SHORT,
    time: Boolean = false
) {

    if (this == null || msg.isNullOrEmpty()) {
        return
    }

    toastHandler.post {
        var realTag = tag ?: this.javaClass.simpleName

        if (time) {
            //这个单独用一个tag来存储
            realTag += "time"
        }

        try {
            val toast = tagMap[realTag]?.get() ?: Toast.makeText(this, msg, duration)
            toast?.setGravity(Gravity.CENTER, 0, 0)
            toast?.setText(msg)

            if (time) {
                //10s秒钟只显示一次
                val toastTime = toast.view?.getTag(R.id.last_toast_time) as? Long? ?: 0L
                val currentTime = System.currentTimeMillis()
                if (currentTime - toastTime <= 10000L) {
                    return@post
                }
                toast.view?.setTag(R.id.last_toast_time, currentTime)
            }

            toast.duration = duration
            tagMap[realTag] = WeakReference(toast)
            toast.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * 取消当前页面的toast
 */
fun Context?.cancelToast(tag: String? = null) {
    if (this == null) {
        return
    }

    toastHandler.post {
        val realTag = tag ?: this.javaClass.simpleName
        val toast = tagMap[realTag]
        toast?.get()?.cancel()
    }
}