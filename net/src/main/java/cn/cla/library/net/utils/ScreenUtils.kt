package cn.cla.library.net.utils

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import cn.cla.library.net.topActivity


internal val screenWidth: Int
    get() {
        val wm = getApp().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        wm.defaultDisplay.getRealSize(point)
        return point.x
    }

internal val screenHeight: Int
    get() {
        val wm = getApp().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        wm.defaultDisplay.getRealSize(point)
        return point.y
    }

/**
 * 屏幕中除了状态栏和导航栏的高度
 */
internal val screenContentHeight: Int
    get() = screenHeight - statusBarHeight - navBarHeight

internal val statusBarHeight: Int
    get() {
        val resources = getApp().resources
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return resources.getDimensionPixelSize(resourceId)
    }

/** 导航栏的高度 */
internal val navBarHeight: Int
    get() {
        val res = getApp().resources
        val resourceId = res.getIdentifier("navigation_bar_height", "dimen", "android")
        val height = if (resourceId != 0) res.getDimensionPixelSize(resourceId) else 0

        val topAty = topActivity
        return if (topAty != null && isNavBarVisible(topAty)) {
            height
        } else {
            0
        }
    }

internal fun isNavBarVisible(activity: Activity) = isNavBarVisible(activity.window)

internal fun isNavBarVisible(window: Window): Boolean {
    var isVisible = false
    val decorView = window.decorView as ViewGroup
    var i = 0
    val count = decorView.childCount
    while (i < count) {
        val child: View = decorView.getChildAt(i)
        val id: Int = child.id
        if (id != View.NO_ID) {
            val resourceEntryName: String = getResNameById(id)
            if ("navigationBarBackground" == resourceEntryName && child.visibility == View.VISIBLE) {
                isVisible = true
                break
            }
        }
        i++
    }
    if (isVisible) {
        // 对于三星手机，android10以下非OneUI2的版本，比如 s8，note8 等设备上，
        // 导航栏显示存在bug："当用户隐藏导航栏时显示输入法的时候导航栏会跟随显示"，会导致隐藏输入法之后判断错误
        // 这个问题在 OneUI 2 & android 10 版本已修复
        if (isSamsung && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                return Settings.Global.getInt(getApp().contentResolver, "navigationbar_hide_bar_enabled") == 0
            } catch (ignore: Exception) {
            }
        }
        val visibility = decorView.systemUiVisibility
        isVisible = visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0
    }
    return isVisible
}

