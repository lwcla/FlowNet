package cn.cla.net.demo

import android.app.Application
import android.content.Context
import cn.cla.library.net.config.INetProvider
import cn.cla.library.net.config.NetConfig

class App : Application() {

    companion object {
        var app: App? = null
        val instance: Application
            get() = app ?: throw NullPointerException("app instance is null !!!")
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        app = this
    }

    override fun onCreate() {
        super.onCreate()

        NetConfig.init(object : INetProvider {
            override fun getBaseUrl() = "https://www.wanandroid.com"
            override fun tokenFail(msg: String) {}
        })
    }
}