package cn.cla.library.net.vm

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import cn.cla.library.net.config.NetConfig
import cn.cla.library.net.entity.Resource
import cn.cla.library.net.utils.cancelJob
import cn.cla.library.net.utils.saveJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

interface LaunchObserverInf<T> {

    /**
     * 拿到上次请求返回的值
     */
    val value: Resource<T>?

    /**
     * 注册观察者
     * @param owner 被观察的对象
     * @param minActiveState 收集上游流的Lifecycle.State 。 如果生命周期低于该状态，则收集将停止，如果再次处于该状态，则将重新启动。意思是，设置了这个参数之后，每次activity/fragment走到这个状态的时候，就会自动重新发送请求
     * @param r 请求结果
     */
    fun observe(owner: LifecycleOwner, minActiveState: Lifecycle.State = Lifecycle.State.STARTED, r: ResourceCall<T>)

    /**
     * 注册观察者 生命周期跟随ViewModel，如果viewModel销毁，那么请求也会被停止
     * @param r 请求结果
     */
    fun observe(r: ResourceCall<T>)

    /**
     * 注册观察者，生命周期跟随app，即使viewModel销毁了，请求也会继续
     * @param r 请求结果
     */
    fun observeForever(r: ResourceCall<T>)
}

internal class LaunchObserverImpl<T>(
    scope: CoroutineScope,
    map: ConcurrentHashMap<String, Job?>,
    private val key: String?,
) : LaunchObserverInf<T>, ObserverResultInf<T> {

    private val ref = WeakReference(scope)
    private val mapRef = WeakReference(map)

    private val map get() = mapRef.get()

    private var resource: Resource<T>? = null
    private var result: ResourceCall<T>? = null
    internal var request: (suspend (owner: LifecycleOwner?, state: Lifecycle.State?) -> Unit)? = null

    override fun setResult(res: Resource<T>) {
        resource = res
        result?.invoke(res)
    }


    /** 拿到上次请求返回的值 */
    override val value: Resource<T>? get() = resource

    /**
     * 注册观察者 等到调这个方法的时候，才开始执行请求
     * @param owner 被观察的对象
     * @param minActiveState 收集上游流的Lifecycle.State 。 如果生命周期低于该状态，则收集将停止，如果再次处于该状态，则将重新启动。意思是，设置了这个参数之后，每次activity/fragment走到这个状态的时候，就会自动重新发送请求
     * @param r 请求结果
     */
    override fun observe(owner: LifecycleOwner, minActiveState: Lifecycle.State, r: ResourceCall<T>) {
        result = r
        key.cancelJob(map)
        //这里用的协程作用域是viewModel级别的
        val job = ref.get()?.launch { request?.invoke(owner, minActiveState) }
        key.saveJob(map, job)
    }

    /**
     * 注册观察者 生命周期跟随ViewModel，如果viewModel销毁，那么请求也会被停止
     * @param r 请求结果
     */
    override fun observe(r: ResourceCall<T>) {
        result = r
        key.cancelJob(map)
        //这里用的协程作用域是viewModel级别的
        val job = ref.get()?.launch { request?.invoke(null, null) }
        key.saveJob(map, job)
    }

    /**
     * 注册观察者，生命周期跟随app，即使viewModel销毁了，请求也会继续
     * @param r 请求结果
     */
    override fun observeForever(r: ResourceCall<T>) {
        result = r

        //这里用的协程作用域是App级别的
        NetConfig.scope.launch(Dispatchers.Main) {
            //这里不是主线程
            request?.invoke(null, null)
        }
    }
}
