package cn.cla.net.demo.vm

import android.app.Application
import cn.cla.library.net.vm.BaseViewModel

class MainVm(app: Application) : BaseViewModel(app) {

    private val repo by lazy { MainRepository() }

    private val _homeDataFlow = createLiveFlow<String>()
    val homeDataState = _homeDataFlow.switchFlow {
        repo.loadHomeBanner()
    }

    private val _loadList = createLiveFlow<Int>()
    val loadList = _loadList.switchFlow {
        repo.loadList(it)
    }

    private val _loadSecondFragment1 = createLiveFlow<Int>()
    val loadSecondFragment1 = _loadSecondFragment1.switchFlow {
        repo.loadList(it)
    }

    var pageIndex: Int = 0
    var fragmentPageIndex: Int = 0

    /** 首页banner */
    fun loadHomeBanner(force: Boolean) {
        _homeDataFlow.setValue("这是设置的值", forceRequest = force)
    }

    fun loadList(pageIndex: Int, refresh: Boolean, force: Boolean) {
        _loadList.setValue(value = pageIndex, isRefresh = refresh, forceRequest = force)
    }

    fun loadSecondFragment1(pageIndex: Int, refresh: Boolean, force: Boolean) {
        _loadSecondFragment1.setValue(value = pageIndex, isRefresh = refresh, forceRequest = force)
    }

    fun loadHomeBanner() = repo.loadHomeBanner().launch()

    fun loadList1() = repo.loadList(1).launch()
}

