package cn.cla.library.net.vm

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import cn.cla.library.net.config.ForeverActionEvent
import cn.cla.library.net.config.NetConfig
import cn.cla.library.net.entity.Resource
import cn.cla.library.net.entity.complete
import cn.cla.library.net.utils.cancelJob
import cn.cla.library.net.utils.logI
import cn.cla.library.net.utils.saveJob
import com.hjq.gson.factory.GsonFactory
import com.jeremyliao.liveeventbus.LiveEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface LaunchObserverInf<T> {

    /** 拿到上次请求返回的值 */
    val value: Resource<T>?

    /**
     * 注册观察者
     * @param minActiveState 收集上游流的Lifecycle.State 。 如果生命周期低于该状态，则收集将停止，如果再次处于该状态，则将重新启动。意思是，设置了这个参数之后，每次activity/fragment走到这个状态的时候，就会自动重新发送请求
     * @param r 请求结果
     */
    fun LifecycleOwner.observe(minActiveState: Lifecycle.State? = null, r: ResourceCall<T>): Job?

    /** 注册观察者，生命周期跟随app，即使viewModel销毁了，请求也会继续 */
    fun observeForever(action: String)
}

/**
 * 注册观察者
 * 在view中使用
 */
context (View)
fun <T> LaunchObserverInf<T>.observe(
    minActiveState: Lifecycle.State? = null,
    call: ResourceCall<T>
): Job? {
    assert(isAttachedToWindow) { "LaunchObserverInf observe isAttachedToWindow is false !!!" }
    return findViewTreeLifecycleOwner()?.observe(minActiveState, call)
}

/**
 * 返回请求结果，这个不会返回loading状态，只会返回一次结果
 * 在view中使用
 */
context (View)
suspend fun <T> LaunchObserverInf<T>.await(
    minActiveState: Lifecycle.State? = null
) = suspendCoroutine<Resource<T>> { cont ->
    observe(minActiveState) {
        it.complete {
            runCatching { cont.resume(it) }
        }
    }
}

/**
 * 注册观察者
 * 在fragment中使用
 */
context (Fragment)
fun <T> LaunchObserverInf<T>.observe(
    minActiveState: Lifecycle.State? = null,
    call: ResourceCall<T>
) = viewLifecycleOwner.observe(minActiveState, call)

/**
 * 返回请求结果，这个不会返回loading状态，只会返回一次结果
 * 在fragment中使用
 */
context (Fragment)
suspend fun <T> LaunchObserverInf<T>.await(
    minActiveState: Lifecycle.State? = null
) = suspendCoroutine<Resource<T>> { cont ->
    observe(minActiveState) {
        it.complete {
            runCatching { cont.resume(it) }
        }
    }
}

/**
 * 注册观察者
 * 在activity中使用
 */
context (AppCompatActivity)
fun <T> LaunchObserverInf<T>.observe(
    minActiveState: Lifecycle.State? = null,
    call: ResourceCall<T>
) = this@AppCompatActivity.observe(minActiveState, call)

/**
 * 返回请求结果，这个不会返回loading状态，只会返回一次结果
 * 在activity中使用
 */
context (AppCompatActivity)
suspend fun <T> LaunchObserverInf<T>.await(
    minActiveState: Lifecycle.State? = null
) = suspendCoroutine<Resource<T>> { cont ->
    observe(minActiveState) {
        it.complete {
            runCatching { cont.resume(it) }
        }
    }
}


internal class LaunchObserverImpl<T>(
    private val map: ConcurrentHashMap<String, Job?>,
    private val key: String?,
    private val flow: Flow<Resource<T>>
) : LaunchObserverInf<T> {

    companion object {
        private val TAG = LaunchObserverInf::class.java.simpleName
    }

    private var resource: Resource<T>? = null


    /** 拿到上次请求返回的值 */
    override val value: Resource<T>? get() = resource

    /**
     * 注册观察者 等到调这个方法的时候，才开始执行请求
     * @param minActiveState 收集上游流的Lifecycle.State 。 如果生命周期低于该状态，则收集将停止，如果再次处于该状态，则将重新启动。意思是，设置了这个参数之后，每次activity/fragment走到这个状态的时候，就会自动重新发送请求
     * @param r 请求结果
     */
    override fun LifecycleOwner.observe(
        minActiveState: Lifecycle.State?,
        r: ResourceCall<T>
    ): Job {
        key.cancelJob(map)
        val job = lifecycleScope.launch {
            flow.onCompletion {
                logI("$TAG observe onCompletion minActiveState=$minActiveState")
            }.let {
                if (minActiveState != null) {
                    it.flowWithLifecycle(lifecycle, minActiveState)
                } else {
                    it
                }
            }.collect {
                resource = it
                r.invoke(it)
            }
        }

        key.saveJob(map, job)
        return job
    }

    /**
     * 注册观察者，生命周期跟随app，即使viewModel销毁了，请求也会继续
     * 也正是因为这个原因，所以不能直接将请求结果返回，需要通过一个MutableSharedFlow来获取这里的请求结果
     */
    override fun observeForever(action: String) {
        //这里用的协程作用域是App级别的
        NetConfig.scope.launch(Dispatchers.Default) {
            flow.collect {
                val json = GsonFactory.getSingletonGson().toJson(it)
                LiveEventBus.get<ForeverActionEvent>(action).post(ForeverActionEvent(action, json))
            }
        }
    }
}
