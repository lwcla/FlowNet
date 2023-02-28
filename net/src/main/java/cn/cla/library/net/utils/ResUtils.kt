package cn.cla.library.net.utils

internal fun getResNameById(id: Int): String {
    return try {
        getApp().resources.getResourceEntryName(id)
    } catch (ignore: java.lang.Exception) {
        ""
    }
}