package cn.cla.net.demo.vm

import cn.cla.library.net.RequestBuilder
import cn.cla.library.net.entity.Resource
import cn.cla.net.demo.entity.HomeBannerEntity
import cn.cla.net.demo.entity.PageEntity
import cn.cla.net.demo.net.requestBaseByFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

class MainRepository {
    /**
     * 首页banner
     */
    fun loadHomeBanner() = requestBaseByFlow<MainService, List<HomeBannerEntity>>(call = {
        loadHomeBanner()
    }) {
        netWay = RequestBuilder.NetWay.ONLY_NET
        autoShowLoading = true
        loadingText = "正在加载首页banner数据"
        loadingDismissDelay = 1000
    }.onEach {
        delay(5000)
    }

    fun loadList(pageIndex: Int) = flow {

        val list = mutableListOf<String>()
        repeat(10) {
            list.add("$pageIndex-$it")
        }

        emit(Resource.success(PageEntity(pageIndex = pageIndex, pageSize = 10, list = list)))
    }.onEach {
        delay(500)
    }
}