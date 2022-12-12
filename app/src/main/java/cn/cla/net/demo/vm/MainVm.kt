package cn.cla.net.demo.vm

import android.app.Application
import cn.cla.library.net.BaseViewModel
import cn.cla.net.demo.net.requestBaseByFlow

class MainVm(app: Application) : BaseViewModel(app) {

    private  val repo by lazy { MainRepository() }

    /**
     * 首页banner
     */
    fun loadHomeBanner() = repo.loadHomeBanner().launch()

}