package cn.cla.library.net.vm

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import cn.cla.library.net.entity.Resource
import cn.cla.library.net.entity.convert
import cn.cla.library.net.entity.success
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

interface StateObserveInf<T> {

    /**
     * 拿到上次请求返回的值
     */
    val value: Resource<T>?

    /**
     * 注册观察者
     * @param owner 被观察的对象
     * @param call 请求结果
     */
    fun observe(owner: LifecycleOwner, minActiveState: Lifecycle.State? = null, call: ResourceCall<T>)

}

class StateObserveImpl<T>(scope: CoroutineScope) : StateObserveInf<T>, ObserverResultInf<T> {

    companion object {
        private val TAG = StateObserveImpl::class.java.simpleName
    }

    private val scopeRef = WeakReference(scope)
    private val stateFlow by lazy { MutableSharedFlow<Resource<T>>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }
    private var requestJob: Job? = null

    @Volatile
    private var resource: Resource<T>? = null

    internal var request: (suspend (owner: LifecycleOwner?, state: Lifecycle.State?) -> Unit)? = null

    override val value get() = resource.let { if (it?.success == true) it else null }

    override fun setResult(res: Resource<T>, isRefresh: Boolean) {
        scopeRef.get()?.launch {
            if (isRefresh) {
                resource = null
            }

            stateFlow.emit(res)
        }
    }

    override fun observe(owner: LifecycleOwner, minActiveState: Lifecycle.State?, call: ResourceCall<T>) {
        var job: Job? = null

        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                job?.cancel()
                owner.lifecycle.removeObserver(this)
            }
        })

        job = scopeRef.get()?.launch {

            resource?.let {
                launch { call.invoke(it) }
            }

            requestJob?.cancel()
            requestJob = scopeRef.get()?.launch(Dispatchers.Default) {
                request?.invoke(owner, minActiveState)
            }

            stateFlow.onCompletion {
                Log.i(TAG, "StateObserveImpl.observe onCompletion  owner=${owner}")
            }.catch {
                it.printStackTrace()
            }.collect {

                it.success {
                    val pageInf = this as? PageCacheInf<T>
                    if (pageInf == null) {
                        resource = it
                    } else {
                        val cacheData = resource?.data?.let { data -> pageInf.pageCache(data) } ?: it.data
                        cacheData?.let { cache ->
                            resource = it.convert(cache)
                        }
                    }
                }

                println("StateObserveImpl.observe lwl resource=${resource?.data}")

                call.invoke(it)
            }
        }
    }

}

interface PageCacheInf<T> {
    fun pageCache(cache: T): T
}

