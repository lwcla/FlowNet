package cn.cla.library.net.entity

import cn.cla.library.net.RequestBuilder
import cn.cla.library.net.utils.ifNullOrBlank
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

enum class ResourceState {
    Loading, Failure, Success
}

/**
 *
 * @param T
 * @property state [ResourceState]
 * @property data
 * @property httpCode
 * @property code
 * @property message
 * @property errorToast
 * @property isReadFromCacheBeforeNet 数据是否从本地缓存读取，只有在[cacheBeforeNet]为true时，这个值才有可能为true
 */
data class Resource<T>(
    val state: ResourceState,
    val data: T?,
    val httpCode: Int = 0,
    val code: Int = 0,
    var message: String? = null,
    var errorToast: (() -> Unit)? = null,
    var errorToastWithTag: ((String) -> Unit)? = null,
    var isReadFromCacheBeforeNet: Boolean = false,
    var isMockData: Boolean = false,
    var field: String? = null,
) {

    val netError: Boolean
        get() = httpCode == 504

    val success: Boolean
        get() = state == ResourceState.Success

    val failure: Boolean
        get() = state == ResourceState.Failure

    val loading: Boolean
        get() = state == ResourceState.Loading

    val complete: Boolean get() = success || failure

    companion object {

        fun <T> loading() = Resource<T>(ResourceState.Loading, null, code = 0)

        fun <T> failure(
            message: String? = null,
            httpCode: Int,
            code: Int = 0,
            data: T? = null
        ) = Resource<T>(
            ResourceState.Failure,
            data,
            code = code,
            httpCode = httpCode,
            message = message
        )

        fun <T> success(data: T?, code: Int = 200) =
            Resource(ResourceState.Success, data = data, code = code)
    }
}

/**
 * 将普通数据转为Resource
 */
fun <T : Any> T?.toResource() = Resource.success(this, 200)

fun <T, R> Resource<T>.convert(data: R?) = Resource<R>(
    state = state,
    data = data,
    httpCode = httpCode,
    code = code,
    message = message,
    errorToast = errorToast,
    isReadFromCacheBeforeNet = isReadFromCacheBeforeNet,
    field = field,
)

/**
 * 合并Resource
 * @param bothSuc 是否在两个请求都成功的情况下，才算是请求成功
 * @param block 合并数据
 * @return Resource<R>
 */
suspend inline fun <T1, T2, R> Resource<T1>.combine(
    r2: Resource<T2>,
    bothSuc: () -> Boolean = { success && r2.success },
    block: (T1?, T2?) -> R
): Resource<R> {

    //这些code什么的都是取请求失败的那个resource
    val combineSuccess = bothSuc()
    val combineState = if (combineSuccess) ResourceState.Success else ResourceState.Failure
    val combineHttpCode = if (combineSuccess) {
        200
    } else {
        if (r2.failure) r2.httpCode else httpCode
    }
    val combineCode = if (r2.failure) r2.code else code
    val combineMessage = if (r2.failure) r2.message else message
    val combineToast = if (r2.failure) r2.errorToast else errorToast
    val combineToastWithTag = if (r2.failure) r2.errorToastWithTag else errorToastWithTag
    val combineField =
        "${field.ifNullOrBlank(nullStr = "", endStr = "/*/")}${r2.field.ifNullOrBlank(nullStr = "")}"

    return Resource(
        state = combineState,
        data = block(data, r2.data),
        httpCode = combineHttpCode,
        code = combineCode,
        message = combineMessage,
        errorToast = combineToast,
        isReadFromCacheBeforeNet = isReadFromCacheBeforeNet || r2.isReadFromCacheBeforeNet,
        field = combineField,
        errorToastWithTag = combineToastWithTag
    )
}

/**
 * 请求时，先返回本地缓存的数据，然后返回网络数据，如果这两份数据返回的间隔时间不超过500ms，那么就会返回一次数据
 * 这个方法主要是和[combineResource]方法搭配使用，这样就能保证即使是合并了多个请求，但是不会返回到view层，导致多次刷新ui
 * @param needCache 手动刷新的时候，就不用读取缓存数据，只需要读取网络数据就可以了
 * @param createFlow Function1<NetWay, Flow<Resource<T>>>
 * @return Flow<Resource<T>>
 */
inline fun <T> requestFromCacheBeforeNet(
    needCache: Boolean = true,
    crossinline createFlow: (RequestBuilder.NetWay) -> Flow<Resource<T>>
): Flow<Resource<T>> {

    //网络请求成功
    val netReadSuccess = AtomicBoolean(false)
    //本地缓存读取成功
    val cacheReadSuccess = AtomicBoolean(false)

    val cacheFlow = flow {
        //在不需要缓存的情况下，不去读取缓存数据
        if (needCache) {
            createFlow(RequestBuilder.NetWay.ONLY_CACHE).collect { emit(it) }
        }
    }.onEach {
        it.success { cacheReadSuccess.compareAndSet(false, true) }
    }.filter {
        //本地缓存读取成功以及网络请求不成功的情况下，才返回本地缓存数据
        //一旦网络请求成功，那么本地缓存的数据就不用返回了
        //缓存数据读取失败的话，也不用返回
        it.success && !netReadSuccess.get()
    }

    val netFlow = createFlow(RequestBuilder.NetWay.ONLY_NET_BUT_SAVE_CACHE).onEach {
        it.successOrNull { netReadSuccess.compareAndSet(false, true) }
    }.filter {
        //网络数据请求成功或者本地缓存请求失败的情况下，才返回网络数据
        //如果网络数据请求失败了，这个时候本地缓存请求成功了，那么就只需要返回缓存数据就可以了
        //否则本地缓存读取成功了，但是网络数据读取失败，结果失败的网络数据冲掉了成功的本地缓存数据
        //导致最后ui显示的时请求失败的状态
        it.success || !cacheReadSuccess.get()
    }

    //merge 会让两个flow同时启动，但是网络返回的数据才是最新的
    //一旦网络请求成功，那么本地缓存的数据就不用返回了
    return listOf(cacheFlow, netFlow).merge().debounce(500)
}


/**
 * 合并两个flow请求
 *
 * @param flow 另外一个请求
 * @param waitComplete 是否需要等到这两个请求全部返回结果之后，再将两个请求的结果返回到view。如果设置为false，那么这个方法就会不止一次返回结果
 * @receiver
 */
inline fun <T1, T2, R> Flow<Resource<T1>>.combineResource(
    flow: Flow<Resource<T2>>,
    waitComplete: Boolean = true,
    crossinline block: suspend (Resource<T1>, Resource<T2>) -> Resource<R>
) = combineTransform(flow) { r1, r2 ->

    if (waitComplete && !r1.complete) {
        return@combineTransform
    }

    if (waitComplete && !r2.complete) {
        return@combineTransform
    }

    emit(block(r1, r2))
}

inline fun <ResultType> Resource<ResultType>.success(
    successBlock: ResultType.() -> Unit
) = apply {
    if (this.success) {
        this.data?.let { successBlock(it) }
    }
}

inline fun <ResultType> Resource<ResultType>.successOrNull(
    successBlock: (ResultType?) -> Unit
) = apply {
    if (this.success) {
        successBlock(this.data)
    }
}

inline fun <ResultType> Resource<ResultType>.complete(
    block: Resource<ResultType>.() -> Unit
) = apply {
    if (this.success || this.failure) {
        block(this)
    }
}

inline fun <ResultType> Resource<ResultType>.fail(
    includeNetError: Boolean = true,
    failBlock: Resource<ResultType>.() -> Unit
) = apply {
    if (this.failure && (includeNetError || !netError)) {
        failBlock(this)
    }
}

inline fun <ResultType> Resource<ResultType>.loading(
    block: () -> Unit
) = apply {
    if (this.loading) {
        block()
    }
}

inline fun <ResultType> Resource<ResultType>.empty(
    block: () -> Unit
) = apply {
    if (success && this.data == null) {
        block()
    }
}

/**
 * 网络异常
 */
inline fun <ResultType> Resource<ResultType>.netError(
    block: () -> Unit
) = apply {
    if (netError) {
        block()
    }
}

data class CallResult<T>(
    val success: Boolean,
    val httpCode: Int,
    val result: T? = null,
    val httpMsg: String? = null
)

/**
 * 请求成功
 */
fun <T> CallResult<T>?.suc() =
    this != null && success && result != null && httpCode / 100 != 4 && httpCode / 100 != 5 && httpCode >= 0

suspend fun <T> CallResult<T>.toResource(
    covert: (suspend T.(Boolean) -> T)? = null,
    covertAnyWay: (suspend T?.(Boolean) -> T?)? = null,
    isReadFromCacheBeforeNet: Boolean = false,
    paramsField: String? = null
): Resource<T> {
    val result1 = covertAnyWay?.invoke(result, isReadFromCacheBeforeNet) ?: result

    return if (suc()) {
        val data = result1?.run { covert?.invoke(this, isReadFromCacheBeforeNet) ?: this }
        Resource.success<T>(data, code = httpCode)
    } else {
        Resource.failure<T>(message = httpMsg, httpCode = httpCode, data = result1)
    }.apply {
        field = paramsField
    }
}