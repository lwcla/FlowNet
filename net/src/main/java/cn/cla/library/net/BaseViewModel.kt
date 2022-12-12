package cn.cla.library.net

import android.app.Application
import androidx.lifecycle.*
import cn.cla.library.net.config.NetConfig
import cn.cla.library.net.entity.Resource
import cn.cla.library.net.entity.complete
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface ViewModelLaunchObserver<T> {
    /**
     * 注册观察者
     * @param owner 被观察的对象
     * @param minActiveState 收集上游流的Lifecycle.State 。 如果生命周期低于该状态，则收集将停止，如果再次处于该状态，则将重新启动。意思是，设置了这个参数之后，每次activity/fragment走到这个状态的时候，就会自动重新发送请求
     * @param r 请求结果
     */
    fun observe(owner: LifecycleOwner, minActiveState: Lifecycle.State = Lifecycle.State.STARTED, r: (Resource<T>) -> Unit)

    /**
     * 注册观察者 生命周期跟随ViewModel，如果viewModel销毁，那么请求也会被停止
     * @param r 请求结果
     */
    fun observe(r: (Resource<T>) -> Unit)

    /**
     * 注册观察者，生命周期跟随app，即使viewModel销毁了，请求也会继续
     * @param r 请求结果
     */
    fun observeForever(r: (Resource<T>) -> Unit)

    /**
     * 拿到上次请求返回的值
     */
    fun getValue(): Resource<T>?
}

open class BaseViewModel(app: Application) : AndroidViewModel(app) {

    val app get() = getApplication<Application>()

    private val map by lazy { mutableMapOf<String, Job?>() }

    /**
     * 发送请求
     * @param key 如果不需要取消上次的请求，那么key设置为null
     * @return ViewModelLaunchObserver<T> 请求结果
     */
    fun <T> Flow<Resource<T>>.launch(
        key: String? = getMethodName(),
    ): ViewModelLaunchObserver<T> {
        val result = LaunchObserverImpl<T>(app, viewModelScope, map, key)
        result.request = { owner, minActiveState ->
            request(this@launch, owner, minActiveState, result)
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
    inline fun <reified T> createLiveFlow() = MutableSharedFlow<T>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun <Q, T> MutableSharedFlow<Q>.switchMap(
        key: String? = null,
        flow: suspend (Q) -> Flow<Resource<T>>,
    ): ViewModelLaunchObserver<T> {
        val result = LaunchObserverImpl<T>(app, viewModelScope, map, key)
        var repeatJob: Job? = null
        result.request = { owner, minActiveState ->
            collect {
                //flowWithLifecycle 会阻塞当前协程，如果shareFlow发送了另外一个值过来
                //collect虽然会收集到这个值，但是因为flowWithLifecycle阻塞了这个协程，所以最终并不会执行flow流
                //所以把这一块放在另外一个协程中，同时为了防止这里为了防止同时存在多个flowWithLifecycle方法在运行，在启动之前，先取消
                repeatJob?.cancel()
                repeatJob = viewModelScope.launch {
                    request(flow(it), owner, minActiveState, result)
                }
            }
        }
        return result
    }

    /**
     * value 不能设置成空字符串
     */
    fun <Q> MutableSharedFlow<Q>.setValue(value: Q) {
        viewModelScope.launch { emit(value) }
    }

    private suspend fun <T> request(
        flow: Flow<Resource<T>>,
        owner: LifecycleOwner?,
        minActiveState: Lifecycle.State?,
        result: LaunchObserverImpl<T>
    ) {
        if (owner != null && minActiveState != null) {
            //收集上游流的Lifecycle.State 。 如果生命周期低于该状态，则收集将停止，如果再次处于该状态，则将重新启动。
            //意思是，设置了这个参数之后，每次activity/fragment走到这个状态的时候，就会自动重新发送请求
            flow.flowWithLifecycle(owner.lifecycle, minActiveState)
        } else {
            flow
        }.onStart {
            emit(Resource.loading<T>())
        }.onEmpty {
            emit(Resource.failure(httpCode = -1, code = -1))
        }.catch {
            it.printStackTrace()
            //流出现异常，上报错误数据
            emit(Resource.failure(httpCode = -1, code = -1))
        }.cancellable().flowOn(Dispatchers.Default).collectLatest { //有新值发出时，如果此时上个收集尚未完成，则会取消掉上个值的收集操作
            it.complete { result.resource = this }
            result.result?.invoke(it)
        }
    }

    private fun getMethodName(): String {
        val traces = Thread.currentThread().stackTrace.filter {
            it.className == this.javaClass.name && it.methodName != "getMethodName"
        }

        return traces.firstOrNull()?.methodName ?: ""
    }

    private class LaunchObserverImpl<T>(
        app: Application,
        scope: CoroutineScope,
        map: MutableMap<String, Job?>,
        private val key: String?
    ) : ViewModelLaunchObserver<T> {
        private val appRef = WeakReference(app)
        private val ref = WeakReference(scope)
        private val mapRef = WeakReference(map)

        private val map get() = mapRef.get()

        var resource: Resource<T>? = null
        var result: ((Resource<T>) -> Unit)? = null
        var request: (suspend (owner: LifecycleOwner?, state: Lifecycle.State?) -> Unit)? = null

        //等到调这个方法的时候，才开始执行请求
        override fun observe(owner: LifecycleOwner, minActiveState: Lifecycle.State, r: (Resource<T>) -> Unit) {
            result = r
            cancel()
            //这里用的协程作用域是viewModel级别的
            val job = ref.get()?.launch { request?.invoke(owner, minActiveState) }
            saveJob(job)
        }

        override fun observe(r: (Resource<T>) -> Unit) {
            result = r
            cancel()
            //这里用的协程作用域是viewModel级别的
            val job = ref.get()?.launch { request?.invoke(null, null) }
            saveJob(job)
        }

        override fun observeForever(r: (Resource<T>) -> Unit) {
            result = r

            //这里用的协程作用域是App级别的
            NetConfig.scope.launch(Dispatchers.Main) {
                //这里不是主线程
                request?.invoke(null, null)
            }
        }

        private fun cancel() {
            if (key.isNullOrBlank()) {
                return
            }

            map?.get(key)?.cancel()
        }

        private fun saveJob(job: Job?) {
            if (key.isNullOrBlank()) {
                return
            }

            map?.set(key, job)
        }

        override fun getValue(): Resource<T>? = resource
    }
}