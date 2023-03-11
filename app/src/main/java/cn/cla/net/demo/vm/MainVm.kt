package cn.cla.net.demo.vm

import android.app.Application
import cn.cla.library.net.vm.BaseViewModel
import cn.cla.library.net.vm.PageCacheInf

class MainVm(app: Application) : BaseViewModel(app) {

    private val repo by lazy { MainRepository() }

    private val _homeDataFlow = createLiveFlow<String>()
    val homeDataState = _homeDataFlow.switchMap {
        repo.loadHomeBanner()
    }

    private val _loadList = createLiveFlow<Int>()
    val loadList = _loadList.switchMap {
        repo.loadList(it)
    }

    /** 首页banner */
    fun loadHomeBanner(force: Boolean) {
        _homeDataFlow.setValue("这是设置的值", forceRequest = force)
    }

    fun loadList(pageIndex: Int, refresh: Boolean, force: Boolean) {
        _loadList.setValue(value = pageIndex, isRefresh = refresh, forceRequest = force)
    }
}

data class PageEntityCache(
    var pageIndex: Int,
    var pageSize: Int,
    var list: List<String>
) : PageCacheInf<PageEntityCache> {

    override fun pageCache(cache: PageEntityCache): PageEntityCache {
        val newList = mutableListOf<String>()
        newList.addAll(cache.list)
        newList.addAll(list)
        return PageEntityCache(pageIndex = pageIndex, pageSize = pageSize, list = newList)
    }
}