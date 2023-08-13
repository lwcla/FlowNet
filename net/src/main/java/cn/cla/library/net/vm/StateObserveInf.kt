package cn.cla.library.net.vm

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import cn.cla.library.net.entity.PageCacheInf
import cn.cla.library.net.entity.Resource
import cn.cla.library.net.entity.complete
import cn.cla.library.net.entity.convert
import cn.cla.library.net.entity.success
import cn.cla.library.net.utils.logI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

interface StateObserveInf<T> {

    /**
     * 拿到上次请求返回的值
     */
    val value: Resource<T>?

    val successValue: Resource<T>?
        get() = value.let { if (it?.success == true) it else null }

    fun LifecycleOwner.observe(minActiveState: Lifecycle.State? = null, call: ResourceCall<T>): Job?

}

/**
 * 注册观察者
 * 在view中使用
 */
context (View)
fun <T> StateObserveInf<T>.observe(
    minActiveState: Lifecycle.State? = null,
    call: ResourceCall<T>
): Job? {
    assert(isAttachedToWindow) { "StateObserveInf observe isAttachedToWindow is false !!!" }
    return findViewTreeLifecycleOwner()?.observe(minActiveState, call)
}

/**
 * 注册观察者
 * 在fragment中使用
 */
context (Fragment)
fun <T> StateObserveInf<T>.observe(
    minActiveState: Lifecycle.State? = null,
    call: ResourceCall<T>
) = viewLifecycleOwner.observe(minActiveState, call)

/**
 * 注册观察者
 * 在activity中使用
 */
context (AppCompatActivity)
fun <T> StateObserveInf<T>.observe(
    minActiveState: Lifecycle.State? = null,
    call: ResourceCall<T>
) = this@AppCompatActivity.observe(minActiveState, call)


class StateObserveImpl<T> : StateObserveInf<T> {

    companion object {
        private val TAG = StateObserveImpl::class.java.simpleName
    }

    private val stateFlow = MutableStateFlow<ResourceEntity<T>?>(null)

    @Volatile
    private var resource: Resource<T>? = null

    override val value get() = resource

    internal suspend fun setResult(res: Resource<T>, isRefresh: Boolean) {
        if (isRefresh) {
            resource = null
        }

        stateFlow.emit(ResourceEntity(res))
    }

    override fun LifecycleOwner.observe(
        minActiveState: Lifecycle.State?,
        call: ResourceCall<T>
    ): Job = lifecycleScope.launch {
        observe().onCompletion {
            logI("$TAG observe onCompletion minActiveState=$minActiveState")
        }.let {
            if (minActiveState != null) {
                it.flowWithLifecycle(lifecycle, minActiveState)
            } else {
                it
            }
        }.collect {
            call.invoke(it)
        }
    }

    private suspend fun observe() = flowOf(1).transform { historyData ->

        resource?.let {
            // 发送历史数据
            emit(it)
        }

        stateFlow.filterNotNull().collect {
            val res = it.resource
            val isNewData = it.isNewData

            if (!isNewData.get()) {
                // 这个数据已经被处理过了
                // 这里是页面重建之后，重新observe时，会先发送之前缓存的数据，但是stateFlow也会在collect时发送它保存的数据
                // 但是如果是分页的数据，stateFlow里面只会保存最后加载的那一页，[resource]里面保存的是从第一页开始到最后加载的那一页的数据
                // 所以这里不能直接发送stateFlow的数据，要发[resource]
                return@collect
            }

            res.complete {
                it.isNewData.compareAndSet(true, false)
            }.success {
                val pageInf = this as? PageCacheInf<T>
                if (pageInf == null) {
                    resource = res
                    return@success
                }

                val cacheData = resource?.data?.let { data -> pageInf.pageCache(data) } ?: res.data
                cacheData?.let { cache ->
                    resource = res.convert(cache)
                }
            }

            emit(res)
        }
    }.flowOn(Dispatchers.Default).cancellable()

    /**
     * Resource entity
     *
     * @property resource 数据
     * @property isNewData 这次的数据还没有被处理
     */
    private class ResourceEntity<T>(
        val resource: Resource<T>,
        var isNewData: AtomicBoolean = AtomicBoolean(true)
    )
}


