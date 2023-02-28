package cn.cla.net.demo.vm

import cn.cla.net.demo.entity.BaseData
import cn.cla.net.demo.entity.HomeBannerEntity
import retrofit2.Call
import retrofit2.http.GET

interface MainService {

    companion object {
        /**
         * 首页Banner
         */
        const val HOME_BANNER = "banner/json"
    }

    @GET(HOME_BANNER)
    fun loadHomeBanner(): Call<BaseData<List<HomeBannerEntity>>>

}