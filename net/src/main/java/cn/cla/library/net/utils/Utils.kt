package cn.cla.library.net.utils

import android.app.Application
import android.util.Log
import android.view.ViewGroup
import cn.cla.library.net.NetIntContentProvider
import com.google.gson.Gson
import com.hjq.gson.factory.GsonFactory
import java.lang.reflect.InvocationTargetException


internal const val TAG = "cla_net"

internal val gsonFactory: Gson
    get() = GsonFactory.getSingletonGson().also {
        GsonFactory.setJsonCallback { typeToken, fieldName, jsonToken ->
            val errorMes = "类型解析异常：$typeToken#$fieldName，原数据：$jsonToken"
            logE("GsonFactory--------》$errorMes")
        }
    }

fun String?.ifNullOrBlank(nullStr: String, preStr: String = "", endStr: String = ""): String {
    return if (this.isNullOrBlank()) nullStr else "${preStr}${this}${endStr}"
}

internal const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
internal const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT


internal fun getApp(): Application {
    if (NetIntContentProvider.app != null) {
        return NetIntContentProvider.app!!
    }

    NetIntContentProvider.app = getApplicationByReflect()
    if (NetIntContentProvider.app != null) {
        return NetIntContentProvider.app!!
    }

    throw NullPointerException("application is null !!!")
}

private fun getApplicationByReflect(): Application? {
    try {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val thread = getActivityThread()
        val app = activityThreadClass.getMethod("getApplication").invoke(thread) ?: return null
        return app as Application
    } catch (e: InvocationTargetException) {
        e.printStackTrace()
    } catch (e: NoSuchMethodException) {
        e.printStackTrace()
    } catch (e: IllegalAccessException) {
        e.printStackTrace()
    } catch (e: ClassNotFoundException) {
        e.printStackTrace()
    }
    return null
}

private fun getActivityThread(): Any? {
    val activityThread = getActivityThreadInActivityThreadStaticField()
    return activityThread ?: getActivityThreadInActivityThreadStaticMethod()
}

private fun getActivityThreadInActivityThreadStaticField(): Any? {
    return try {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val sCurrentActivityThreadField = activityThreadClass.getDeclaredField("sCurrentActivityThread")
        sCurrentActivityThreadField.isAccessible = true
        sCurrentActivityThreadField.get(null)
    } catch (e: Exception) {
        Log.e(TAG, "getActivityThreadInActivityThreadStaticField: " + e.message)
        null
    }
}

private fun getActivityThreadInActivityThreadStaticMethod(): Any? {
    return try {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        activityThreadClass.getMethod("currentActivityThread").invoke(null)
    } catch (e: Exception) {
        Log.e(TAG, "getActivityThreadInActivityThreadStaticMethod: " + e.message)
        null
    }
}


