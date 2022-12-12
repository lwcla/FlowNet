package cn.cla.library.net.utils

import android.util.Log
import cn.cla.library.net.utils.MyLog.ASSERT
import cn.cla.library.net.utils.MyLog.DEBUG
import cn.cla.library.net.utils.MyLog.ERROR
import cn.cla.library.net.utils.MyLog.INFO
import cn.cla.library.net.utils.MyLog.VERBOSE
import cn.cla.library.net.utils.MyLog.WARN


internal object MyLog {
    //定义全局的Log开关
    val VERBOSE get() = Log.isLoggable(TAG, Log.VERBOSE)
    val DEBUG get() = Log.isLoggable(TAG, Log.DEBUG)
    val INFO get() = Log.isLoggable(TAG, Log.INFO)
    val WARN get() = Log.isLoggable(TAG, Log.WARN)
    val ERROR get() = Log.isLoggable(TAG, Log.ERROR)
    val ASSERT get() = Log.isLoggable(TAG, Log.ASSERT)
}


internal fun logV(msg: String?) {
    if (msg.isNullOrBlank()) {
        return
    }

    if (!VERBOSE) {
        return
    }

    Log.v(TAG, msg)
}

internal fun logI(msg: String?) {
    if (msg.isNullOrBlank()) {
        return
    }

    if (!INFO) {
        return
    }

    Log.i(TAG, msg)
}

internal fun logD(msg: String?) {
    if (msg.isNullOrBlank()) {
        return
    }

    if (!DEBUG) {
        return
    }

    Log.d(TAG, msg)
}

internal fun logW(msg: String?) {
    if (msg.isNullOrBlank()) {
        return
    }

    if (!WARN) {
        return
    }

    Log.w(TAG, msg)
}

internal fun logE(msg: String?) {
    if (msg.isNullOrBlank()) {
        return
    }

    if (!ERROR) {
        return
    }

    Log.e(TAG, msg)
}

internal fun logA(msg: String?) {
    if (msg.isNullOrBlank()) {
        return
    }

    if (!ASSERT) {
        return
    }

    Log.d(TAG, msg)
}