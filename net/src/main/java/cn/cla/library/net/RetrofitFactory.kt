package cn.cla.library.net

import cn.cla.library.net.config.INetHelper
import cn.cla.library.net.interceptor.*
import cn.cla.library.net.utils.MyLog
import cn.cla.library.net.utils.gsonFactory
import cn.cla.library.net.utils.logI
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import java.util.logging.Level


enum class RetrofitType {
    ONLY_NET,  //只从网络读取数据
    CACHE,   //从网络读取数据，如果连接失败，那么读取缓存数据
    ONLY_CACHE, //只从缓存读取数据
    ONLY_NET_BUT_SAVE_CACHE, //虽然是只从网络获取数据，但是还是要把数据缓存下来
}

/**
 * @author
 * @date  2020/4/12 11:23 AM
 * @version 1.0
 */
object RetrofitFactory {

    private val client = OkHttpClient()

    //这里写成这个样子只是因为楼管需要直接切换项目，不能杀死app来切换
    //这里就是根据baseUrl来获取不同的map，这个map装的是不同type对应的retrofit
    private val retrofitMap = mutableMapOf<String, RetrofitCache>()

    private val tokenInterceptor by lazy { TokenInterceptor() }
    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor("Networking").apply {
            if (MyLog.DEBUG) {
                setLogLevel(Level.INFO)
                setPrintLevel(HttpLoggingInterceptor.PrintLevel.BODY)
            } else {
                setLogLevel(Level.OFF)
                setPrintLevel(HttpLoggingInterceptor.PrintLevel.NONE)
            }
        }
    }

    inline fun <reified T> createService(
        type: RetrofitType = RetrofitType.CACHE,
        baseUrl: String = ""
    ): T = create(T::class.java, type, baseUrl)

    fun <T> create(
        cls: Class<T>,
        type: RetrofitType = RetrofitType.CACHE,
        baseUrl: String = ""
    ): T {
        val cache = getValue(baseUrl, type = type)

        val retrofit = cache.retrofit
        val serviceCache = cache.serviceCache
        var service = serviceCache[cls]?.get()

        if (service != null) {
            return service as T
        }

        synchronized(this) {
            service = serviceCache[cls]?.get()

            if (service == null) {
                service = retrofit.create(cls)
                serviceCache[cls] = WeakReference<Any>(service)
            }
        }

        return service!! as T
    }

    private fun getValue(
        baseUrl: String,
        type: RetrofitType
    ): RetrofitCache {
        var value: RetrofitCache? = null

        var url = baseUrl
        if (url.isBlank()) {
            url = INetHelper.getBaseUrl()
        }

        //本地缓存的retrofit需要重新创建
//        if (type == RetrofitType.ONLY_CACHE) {
//            return Pair(createRetrofit(type, url), null)
//        }

        val key = url + type.toString()
        //同一个url，retrofit会有网络和强制读取本地缓存的情况
        //这里需要区分不同的类别
        if (retrofitMap.containsKey(key)) {
            value = retrofitMap[key]
        }

        if (value != null) {
            return value
        }

        synchronized(this) {
            //再取一次，可能已经被其他线程创建了
            if (retrofitMap.containsKey(key)) {
                value = retrofitMap[key]
            }

            if (value == null) {
                val retrofit = createRetrofit(type, url)
                value = RetrofitCache(retrofit, ServiceCache())
                logI("getValue synchronized create url=$url type=$type value=${value}")

                retrofitMap[key] = value!!
            }
        }

        return value!!
    }

    private fun createRetrofit(type: RetrofitType, baseUrl: String): Retrofit = when (type) {
        RetrofitType.ONLY_NET -> initRetrofit(baseUrl = baseUrl) {
            addInterceptor(OnlyNetInterceptor())
        }

        RetrofitType.CACHE -> initRetrofit(baseUrl = baseUrl) {
            cache(getNetCache())
            addNetworkInterceptor(SaveCacheInterceptor())
            addInterceptor(NetCacheInterceptor())
            connectTimeout(5, TimeUnit.SECONDS) //首页数据请求超时时间设置为5秒
        }

        RetrofitType.ONLY_CACHE -> initRetrofit(baseUrl = baseUrl) {
            cache(getNetCache())
            //缓存连接没有必要keep-alive
            connectionPool(ConnectionPool(5, 1, TimeUnit.SECONDS))
            addInterceptor(ForceCacheInterceptor())
        }

        RetrofitType.ONLY_NET_BUT_SAVE_CACHE -> initRetrofit(baseUrl = baseUrl) {
            cache(getNetCache())
            addNetworkInterceptor(SaveCacheInterceptor())
            addInterceptor(OnlyNetRequestButSaveCacheInterceptor())
        }
    }

    private inline fun initRetrofit(
        baseUrl: String,
        block: OkHttpClient.Builder.() -> Unit
    ): Retrofit {

        //Okhttp对象
        val okHttpClient = with(client.newBuilder()) {

            this.block()

            addInterceptor(HeaderInterceptor())
            //添加日志拦截器
            addInterceptor(loggingInterceptor)
            addInterceptor(tokenInterceptor)

            INetHelper.addInterceptor().forEach { addInterceptor(it) }
            build()
        }

        //创建Retrofit对象
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gsonFactory))
            .build()
    }

    class ServiceCache : MutableMap<Class<*>, WeakReference<Any>> by mutableMapOf()

    data class RetrofitCache(val retrofit: Retrofit, val serviceCache: ServiceCache)
}

