package cn.cla.library.net

import android.app.Activity
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.asLiveData
import cn.cla.library.net.RequestBuilder.NetWay
import cn.cla.library.net.RequestEt.request
import cn.cla.library.net.dialog.ProcessDialog
import cn.cla.library.net.entity.CallResult
import cn.cla.library.net.entity.Resource
import cn.cla.library.net.entity.suc
import cn.cla.library.net.entity.toResource
import cn.cla.library.net.interceptor.TokenFailureException
import cn.cla.library.net.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Invocation
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 网络的基础扩展方法
 *
 * 在设计上，[RequestEt] 在net模块，这个模块应该是通用的，不归属于某个具体的项目
 * 所以BaseBean（项目中返回数据的通用结构）这样的模块不应该放在net模块中
 * 最好的方式就是在自己项目的base模块中，对[request]方法进行一个封装
 */
object RequestEt {

    private val TAG = RequestEt::class.java.simpleName

    /**
     * liveData的方式请求数据
     */
    inline fun <reified Service, reified ReturnType> request(
        noinline call: suspend (Service.() -> CallResult<ReturnType>),
        noinline builder: RequestBuilder<Service, ReturnType, ReturnType>.() -> Unit = {}
    ) = request(
        createService = { type, baseUrl -> RetrofitFactory.createService(type, baseUrl) },
        call = call,
        mapResult = { params ->
            toResource(
                params.processBean,
                params.processBeanAnyWay,
                params.isReadFromCacheBeforeNet,
                params.field
            )
        },
        builder = builder
    )

    /**
     * liveData的方式请求数据
     *
     * @param Service Retrofit的Service
     * @param ResponseType 网络请求时返回的数据类型
     * @param ReturnType 实际上返回的数据类型
     *
     * @param mapResult  通过这个方法可以把 [ResponseType]类型的 的数据转换为 [ReturnType]类型 的数据，并且返回的是[Resource]数据，
     * eg:CallResult<BaseBean<String>> -> Resource<String> ,去掉了BaseBean这一层
     * @param call 请求数据的方法
     */
    fun <Service, ResponseType, ReturnType> request(
        createService: (RetrofitType, String) -> Service,
        call: suspend (Service.() -> CallResult<ResponseType>),
        mapResult: suspend CallResult<ResponseType>.(RequestBuilder<Service, ResponseType, ReturnType>) -> Resource<ReturnType>,
        builder: (RequestBuilder<Service, ResponseType, ReturnType>.() -> Unit)? = null
    ) = requestByFlow(createService, call, mapResult, launchLoading = true, builder).asLiveData()


    /**
     * flow的方式请求数据
     *
     * @param Service Retrofit的Service
     * @param ResponseType 网络请求时返回的数据类型
     * @param ReturnType 实际上返回的数据类型
     *
     * @param mapResult  通过这个方法可以把 [ResponseType]类型的 的数据转换为 [ReturnType]类型 的数据，并且返回的是[Resource]数据，
     * eg:CallResult<BaseBean<String>> -> Resource<String> ,去掉了BaseBean这一层
     * @param launchLoading 是否发送loading消息
     * @param call 请求数据的方法
     */
    fun <Service, ResponseType, ReturnType> requestByFlow(
        createService: (RetrofitType, String) -> Service,
        call: suspend (Service.() -> CallResult<ResponseType>),
        mapResult: suspend CallResult<ResponseType>.(RequestBuilder<Service, ResponseType, ReturnType>) -> Resource<ReturnType>,
        launchLoading: Boolean = false,
        builder: (RequestBuilder<Service, ResponseType, ReturnType>.() -> Unit)? = null
    ): Flow<Resource<ReturnType>> {

        val params = RequestBuilder<Service, ResponseType, ReturnType>()
        builder?.invoke(params)

        val baseUrl = params.baseUrl

        val mockFlow = flowOf(params)
            .filter { it.useMock }
            .map { it.mock?.invoke() }
            .filter { it != null }
            .map {
                params.mockResource(it!!)
            }.onEach {
                it.isMockData = true
                it.isReadFromCacheBeforeNet = false
            }.flowOn(Dispatchers.Default)

        val cacheFlow = flowOf(params)
            .filter { params.cacheEnable }
            .map {
                val service = createService(RetrofitType.ONLY_CACHE, baseUrl)
                call(service)
            }.onEach {
                params.cacheFlowHasSuccess = it.suc()
                params.isReadFromCacheBeforeNet = true
            }.filter {
                it.suc()
            }.map {
                mapResult(it, params)
            }.onEach {
                it.isReadFromCacheBeforeNet = true
            }.filter {
                //只有网络请求还没结束的时候，缓存数据才会发出去
                !params.netFlowHasSuccess
            }.flowOn(Dispatchers.IO).cancellable().catch {
                logE("requestByFlow cache error=$it")
            }

        val netFlow = flowOf(params)
            .filter { params.netEnable }
            .map {

                val type = if (params.cacheIfNetError) {
                    //这个会去读取网络数据，如果失败，则读取缓存数据
                    //网络返回的数据也会缓存一份在本地
                    RetrofitType.CACHE
                } else {
                    if (params.onlyNet) {
                        //这个只会从网络读取数据
                        RetrofitType.ONLY_NET
                    } else {
                        //这个只会从网络读取数据，但是会把网络返回的数据缓存一份在本地
                        RetrofitType.ONLY_NET_BUT_SAVE_CACHE
                    }
                }

                val service = createService(type, baseUrl)
                call(service)
            }.onEach {
                //只有网络请求成功的话，才去拦截缓存数据，否则的话
                //就有可能出现，缓存数据读取成功，网络数据请求失败了，结果只返回了网络数据的情况
                //这样的缓存就没有起到作用
                params.netFlowHasSuccess = it.suc()
                params.isReadFromCacheBeforeNet = false
            }.map {
                mapResult(it, params)
            }.onEach {
                it.isReadFromCacheBeforeNet = false
            }.filter {
                //如果网络数据请求失败了，这个时候本地缓存请求成功了，那么就只需要返回缓存数据就可以了
                it.success || !params.cacheFlowHasSuccess
            }.flowOn(Dispatchers.IO).cancellable().catch {
                logE("requestByFlow net error=$it")
            }

        //发送加载中的状态
        val loadingFlow = flow { emit(Resource.loading<ReturnType>()) }.filter { launchLoading }

        //如果网络数据很快就回来了，那么就不用返回缓存的数据
        //避免频繁的刷新Ui
        val dataFlow = listOf(cacheFlow, netFlow).filter { !params.useMock }.merge().debounce(500)

        return listOf(loadingFlow, mockFlow, dataFlow).merge().onStart {
            params.showLoading()
            if (params.autoShowLoading && params.loadingDismissDelay > 0) {
                //避免弹窗一闪而过
                delay(params.loadingDismissDelay)
            }
        }.onCompletion {
            params.hideLoading()
        }.onEmpty {
            emit(Resource.failure<ReturnType>(httpCode = 504))
        }.cancellable().catch {
            logE("requestByFlow flow list error=$it")
        }.flowOn(Dispatchers.Default)/*.asLiveData()*/
    }

    /**
     * call的请求数据的方法
     */
    suspend inline fun <reified ResultType> Call<ResultType>.callAwait() =
        callAwait(type = ResultType::class.java) { this }

    /**
     * call的请求数据的方法
     * 可以转换请求回来的数据类型，eg：Call<String>.callAwait -> CallResult<Int> ，网络请求返回的是String类型的数据，但是可以转换成Int类型的数据返回回去
     *
     * @param ResponseType 请求数据时返回的数据类型
     * @param ReturnType 真正返回的数据类型
     * @param mapData 通过这个方法可以把 [ResponseType]类型的 的数据转换为 [ReturnType]类型 的数据
     */
    suspend fun <ResponseType, ReturnType> Call<ResponseType>.callAwait(
        type: Class<ResponseType>,
        mapData: ResponseType.() -> ReturnType
    ): CallResult<ReturnType> = withContext(Dispatchers.IO) {

        try {
            val response = execute()
            if (response.code() == 204) {
                //处理delete请求时body为空的情况
                return@withContext CallResult(true, response.code(), null)
            }

            if (response.code() == 403) {
                return@withContext CallResult(false, response.code(), httpMsg = "登录信息失效，请重新登录")
            }

            if (response.code() == 401) {
                return@withContext CallResult(false, response.code(), httpMsg = "身份验证不通过")
            }

            //eg: 用错误的手机号注册时，返回的body为空，response.code==500，但是errorBody中包含后台返回的错误信息
            var body = kotlin.runCatching {
                if (response.isSuccessful) response.body() else null
            }.getOrNull()

            if (body == null) {
                body = kotlin.runCatching {
                    gsonFactory.fromJson(response.errorBody()?.string(), type)
                }.getOrNull()
            }

            if (body == null) {
                val invocation = request().tag(Invocation::class.java)!!
                val method = invocation.method()
                val e = KotlinNullPointerException("Response from ${method.declaringClass.name}.${method.name} was null but response body type was declared as non-null")
                logE("callAwait $e")
                CallResult(false, response.code())
            } else {
                CallResult(true, response.code(), mapData(body))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            when (e) {
                is TokenFailureException -> CallResult(false, 403, httpMsg = "登录信息失效，请重新登录")

                is SocketTimeoutException,
                is ConnectException,
                is UnknownHostException,
                -> CallResult(false, 504)

                else -> CallResult(false, -1)
            }
        }
    }
}

/**
 * 网络请求的参数
 * @param baseUrl retrofit的baseUrl
 * @param processBean 对请求成功的数据进行处理
 * @param processBeanAnyWay 不管是成功还是失败，都可以对数据进行处理
 * @param autoToast 请求出错时，是否需要自动toast错误信息
 * @param autoShowLoading 是否在加载时，自动显示loadingDialog
 * @param loadingText loading的文字
 * @param loadingCancelAble loadingDialog是否可以点击空白处取消
 * @param loadingDismissDelay 请求结束之后，延迟隐藏弹窗的时间
 * @param isReadFromCacheBeforeNet 数据是否从本地缓存读取，只有在[NetWay.CACHE_BEFORE_NET]为true时，这个值才有可能为true
 * @param mock 生成假数据
 * @param useMock 是否使用mock数据
 */
class RequestBuilder<Service, ResponseType, ReturnType>(
    var baseUrl: String = "",
    var processBean: (suspend ReturnType.(Boolean) -> ReturnType)? = null,
    var processBeanAnyWay: (suspend ReturnType?.(Boolean) -> ReturnType?)? = null,
    var autoToast: Boolean = true,
    var autoShowLoading: Boolean = false,
    var loadingText: String = "正在加载",
    var loadingCancelAble: Boolean = true,
    var loadingDismissDelay: Long = 200,
    var isReadFromCacheBeforeNet: Boolean = false,
    var field: String? = null,
    var mock: (suspend () -> ReturnType)? = null,
    var mockResource: (suspend (ReturnType) -> Resource<ReturnType>) = { data -> Resource.success(data, 200) },
    var useMock: Boolean = false,
) {

    /** 网络请求是否已经完成 */
    internal var netFlowHasSuccess = false

    /** 本地缓存是否读取成功 */
    internal var cacheFlowHasSuccess = false

    //数据请求方式
    var netWay: NetWay = NetWay.ONLY_NET

    //是否可以读取缓存数据
    internal val cacheEnable get() = netWay == NetWay.ONLY_CACHE || netWay == NetWay.CACHE_BEFORE_NET

    //是否可以读取网络数据
    internal val netEnable get() = netWay != NetWay.ONLY_CACHE

    //是否只读取网络数据
    internal val onlyNet get() = netWay == NetWay.ONLY_NET

    //优先读取网络数据，如果网络数据获取成功，那么就返回网络数据，如果网络数据读取失败，那么就返回缓存数据
    internal val cacheIfNetError get() = netWay == NetWay.CACHE_IF_NET_FAILED

    private var loadingDialog: DialogFragment? = null

    private val topAty: Activity?
        get() = topActivity
    private val scope: LifecycleCoroutineScope?
        get() = topAty?.lifeCycleScope

    var loading: (Activity, LifecycleCoroutineScope) -> Unit = { activity, lifeScope ->
        lifeScope.launch {
            activity.lifeCycle?.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) = hideLoading(lifeScope)
            })

            loadingDialog = activity.showDialogSimple { ProcessDialog.newInstance(loadingCancelAble, loadingText) }
        }
    }

    var dismissLoading: (LifecycleCoroutineScope) -> Unit = { lifeScope ->
        lifeScope.launch {
            kotlin.runCatching {
                loadingDialog?.dismissAllowingStateLoss()
            }.let { loadingDialog = null }
        }
    }

    fun showLoading() {
        if (!autoShowLoading) {
            return
        }

        val activity = topAty ?: return
        val lifeScope = scope ?: return

        loading(activity, lifeScope)
    }

    fun hideLoading(
        lifeScope: LifecycleCoroutineScope? = scope
    ) {
        if (!autoShowLoading) {
            return
        }

        if (lifeScope == null) {
            return
        }

        dismissLoading(lifeScope)
    }

    /** 数据请求方式 */
    enum class NetWay {
        ONLY_NET,//只读网络数据，也不会保存网络数据到本地
        ONLY_NET_BUT_SAVE_CACHE,//只读取网络数据，但是网络数据返回之后，会保存在本地
        ONLY_CACHE, //只读取缓存
        CACHE_IF_NET_FAILED,//优先读取网络数据，如果网络数据获取成功，那么就返回网络数据，如果网络数据读取失败，那么就返回缓存数据
        CACHE_BEFORE_NET,//先返回本地缓存，拿到网络数据之后，再返回网络数据，如果网络数据返回的比较快，那么就只返回网络数据
    }
}