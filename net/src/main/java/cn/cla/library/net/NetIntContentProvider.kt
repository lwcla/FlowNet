package cn.cla.library.net

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import java.lang.ref.WeakReference

internal var topAtyRef: WeakReference<Activity>? = null
internal val topActivity get() = topAtyRef?.get()

internal class NetIntContentProvider : ContentProvider() {

    companion object {
        internal var app: Application? = null
    }

    override fun onCreate(): Boolean {
        app = context?.applicationContext as? Application?
        app?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                topAtyRef = WeakReference(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                if (topAtyRef?.get() != activity) {
                    topAtyRef = WeakReference(activity)
                }
            }

            override fun onActivityResumed(activity: Activity) {
                if (topAtyRef?.get() != activity) {
                    topAtyRef = WeakReference(activity)
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (topAtyRef?.get() == activity) {
                    topAtyRef?.clear()
                    topAtyRef = null
                }
            }

            override fun onActivityStopped(activity: Activity) {
                if (topAtyRef?.get() == activity) {
                    topAtyRef?.clear()
                    topAtyRef = null
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                if (topAtyRef?.get() == activity) {
                    topAtyRef?.clear()
                    topAtyRef = null
                }
            }
        })

        return true
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}