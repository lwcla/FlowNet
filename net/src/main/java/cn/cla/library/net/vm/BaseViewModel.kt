package cn.cla.library.net.vm

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import cn.cla.library.net.entity.Resource
import cn.cla.library.net.entity.complete
import cn.cla.library.net.vm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


typealias  ResourceCall<T> = (Resource<T>) -> Unit

open class BaseViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private val TAG = BaseViewModel::class.java.simpleName
    }

    val app get() = getApplication<Application>()

    private val map by lazy { ConcurrentHashMap<String, Job?>() }

    /**
     * 发送请求
     * @param key 如果不需要取消上次的请求，那么key设置为null
     * @return ViewModelLaunchObserver<T> 请求结果
     */
    fun <T> Flow<Resource<T>>.launch(
        key: String? = getMethodName(),
    ): LaunchObserverInf<T> {
        val result = LaunchObserverImpl<T>(viewModelScope, map, key)
        result.request = { owner, minActiveState ->
            request(this@launch, true, owner, minActiveState, result)
        }
        return result
    }

    /**
     * 同步请求 参数说明查看 [ViewModelLaunchObserver]
     * @param key 如果不需要取消上次的请求，那么key设置为null
     */
    suspend fun <T> Flow<Resource<T>>.launchAwait(
        owner: LifecycleOwner,
        minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
        key: String? = getMethodName(),
    ) = suspendCoroutine<Resource<T>> { cont ->
        launch(key = key).observe(owner, minActiveState) {
            it.complete { cont.resume(it) }
        }
    }

    /**
     * 同步请求 参数说明查看 [ViewModelLaunchObserver]
     * @param key 如果不需要取消上次的请求，那么key设置为null
     */
    suspend fun <T> Flow<Resource<T>>.launchAwait(
        key: String? = getMethodName(),
    ) = suspendCoroutine<Resource<T>> { cont ->
        launch(key = key).observe {
            it.complete { cont.resume(it) }
        }
    }

    /** 创建热流，统一用这个方法创建，方便以后修改 */
    inline fun <reified T> createLiveFlow() = MutableSharedFlow<StateParams<T>>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun <Q, T> MutableSharedFlow<StateParams<Q>>.switchMap(flow: suspend (Q) -> Flow<Resource<T>>): StateObserveInf<T> {
        val result = StateObserveImpl<T>(viewModelScope)
        var repeatJob: Job? = null
        result.request = { owner, minActiveState ->

            this.onCompletion {
                Log.i(TAG, "switchMap liveFlow onCompletion")
            }.collect {
                val key = it.key
                val forceQuest = it.forceRequest
                val questParams = it.value
                val isRefresh = it.isRefresh

                if (!forceQuest && result.value != null) {
                    //之前请求的结果还在时，如果不是强制去请求，那么就直接返回之前的数据
                    //这种情况在viewpager切换页面时，fragment会销毁再重建，需要把之前请求的数据返回过去
                    return@collect
                }

                //flowWithLifecycle 会阻塞当前协程，如果shareFlow发送了另外一个值过来
                //collect虽然会收集到这个值，但是因为flowWithLifecycle阻塞了这个协程，所以最终并不会执行flow流
                //所以把这一块放在另外一个协程中，同时为了防止这里为了防止同时存在多个flowWithLifecycle方法在运行，在启动之前，先取消
                if (key == null) {
                    repeatJob?.cancel()
                }
                repeatJob = viewModelScope.launch {
                    request(flow(questParams), isRefresh, owner, minActiveState, result)
                }
            }
        }
        return result
    }

    /**
     * 去请求数据
     * value 不能设置成空字符串
     *
     * @param value 请求参数
     * @param isRefresh 是否刷新数据，如果是刷新数据，则会把保存的之前请求的数据清空
     * @param forceRequest 是否即使有之前保存的数据，也强制去请求
     * @param key 如果不需要取消上次的请求，那么key设置为null
     */
    fun <Q> MutableSharedFlow<StateParams<Q>>.setValue(
        value: Q,
        isRefresh: Boolean = true,
        forceRequest: Boolean = true,
        key: String? = getMethodName()
    ) {
        viewModelScope.launch {
            emit(StateParams(value = value, isRefresh = isRefresh, forceRequest = forceRequest, key = key))
        }
    }

    private suspend fun <T> request(
        flow: Flow<Resource<T>>,
        isRefresh: Boolean,
        owner: LifecycleOwner?,
        minActiveState: Lifecycle.State?,
        result: ObserverResultInf<T>
    ) {

        flow.onStart {
            //onStart需要设置在flowWithLifecycle前面，否则因为生命周期自动触发时，不会回调onStart方法
            emit(Resource.loading<T>())
        }.let {
            if (owner != null && minActiveState != null) {
                //收集上游流的Lifecycle.State 。 如果生命周期低于该状态，则收集将停止，如果再次处于该状态，则将重新启动。
                //意思是，设置了这个参数之后，每次activity/fragment走到这个状态的时候，就会自动重新发送请求
                it.flowWithLifecycle(owner.lifecycle, minActiveState)
            } else {
                it
            }
        }.onEmpty {
            emit(Resource.failure(httpCode = -1, code = -1))
        }.catch {
            it.printStackTrace()
            //流出现异常，上报错误数据
            emit(Resource.failure(httpCode = -1, code = -1))
        }.cancellable().flowOn(Dispatchers.Default).collectLatest { //有新值发出时，如果此时上个收集尚未完成，则会取消掉上个值的收集操作
            result.setResult(it, isRefresh)
        }
    }

    private fun getMethodName(): String {
        val traces = Thread.currentThread().stackTrace.filter {
            it.className == this.javaClass.name && it.methodName != "getMethodName"
        }

        return traces.firstOrNull()?.methodName ?: ""
    }

    data class StateParams<T>(
        val value: T,
        val isRefresh: Boolean = true,
        val forceRequest: Boolean = true,
        val key: String?
    )
}




