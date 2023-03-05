package cn.cla.net.demo.vm

import cn.cla.library.net.RequestBuilder
import cn.cla.net.demo.entity.HomeBannerEntity
import cn.cla.net.demo.net.requestBaseByFlow

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
    }
}