package cn.cla.net.demo.net

import cn.cla.library.net.RequestBuilder
import cn.cla.library.net.RequestEt
import cn.cla.library.net.RequestEt.callAwait
import cn.cla.library.net.RetrofitFactory
import cn.cla.library.net.entity.CallResult
import cn.cla.library.net.entity.Resource
import cn.cla.library.net.entity.netError
import cn.cla.library.net.entity.suc
import cn.cla.net.demo.R
import cn.cla.net.demo.entity.BaseData
import cn.cla.net.demo.showToast
import cn.cla.net.demo.utils.lazyNone
import cn.cla.net.demo.utils.toStringValue
import retrofit2.Call

private val serviceIsDown by lazyNone { R.string.the_server_is_down_please_click_retry.toStringValue() }
private val netIsError by lazyNone { R.string.load_failure_simple.toStringValue() }

/**
 * 请求成功
 */
fun <T> CallResult<BaseData<T>>.baseSuc() = suc() && (result?.dataSuc ?: false)

/**
 * 把net模块中[callAwait]返回的数据转成成[Resource]结构
 * 把[BaseData]这一层去掉，[BaseData]中是后台返回的一些基本信息
 *
 * @param covert 对数据进行一些处理，eg:登录成功之后，需要保存token，就可以在这个方法中执行，返回的是不为空的对象
 * @param toastAble 是否需要直接把错误信息弹出来，产品要求，如果页面上没有数据，请求出错时，显示错误页面；如果页面上有数据，请求出错时，就直接toast
 */
suspend fun <S, T> CallResult<BaseData<T>>.toBaseRes(
    params: RequestBuilder<S, BaseData<T>, T>
) = this.result.let { baseData ->

    val errCode = baseData?.errorCode

    val code = if (errCode == null || errCode < 0) {
        httpCode
    } else {
        errCode
    }

    var errMsg = baseData?.errorMsg

    val result = params.processBeanAnyWay?.invoke(
        baseData?.data, params.isReadFromCacheBeforeNet
    ) ?: baseData?.data

    val resource = if (baseSuc()) {
        val data =
            result?.run { params.processBean?.invoke(this, params.isReadFromCacheBeforeNet) ?: this }
        Resource.success<T>(data, code = code)
    } else {
        Resource.failure<T>(httpCode = httpCode, code = code, data = result)
    }

    resource.also { res ->

        //        this?.actionType?.let { type ->
//            when (type and 0xfe) {
//                SHOW_TOAST -> this.message.showToast("Net")
//            }
//        }

//        res.actionType = baseData?.actionType ?: 0
        res.field = params.field

        res.netError { errMsg = netIsError }
        errMsg = errMsg ?: (httpMsg ?: serviceIsDown)

        res.message = errMsg
        //把toast的方法存起来，在页面有数据的时候，直接[errorToast?.invoke()]就可以弹出错误信息
        //而不需要再次去判断是网络异常还是服务器异常
        res.errorToastWithTag = { tag ->
            // 403 的时候, 直接去登录界面就不土司了
            if (res.httpCode != 403) {
                errMsg.showToast(tag)
            }
        }

        res.errorToast = {
            // 403 的时候, 直接去登录界面就不土司了
            res.errorToastWithTag?.invoke(res.message ?: "Net")
        }

        //如果请求成功的话，只是把message保存起来，但是不在这里toast
        //isReadFromCacheBeforeNet 为true时，表示数据是从缓存中读取的，这个时候不要toast
        if (!params.isReadFromCacheBeforeNet && params.autoToast && !baseSuc()) {
            res.errorToast?.invoke()
        }
    }
}

fun <S, T> RequestBuilder<S, BaseData<T>, T>.baseBuild(): RequestBuilder<S, BaseData<T>, T> {

//    loadingText = "正在加载"
//
//    var dialog: ProcessDialog? = null
//    //两个同时发送的请求，都需要showDialog的情况下
//    //如果都使用相同的tag，那么其他一个dialog就会被快速的remove掉
//    val dialogTag = this.hashCode().toString()
//    loading = { activity, lifeScope ->
//        lifeScope.launch {
//            dialog = activity.showDialogSimple {
//                ProcessDialog(loadingCancelAble, loadingText)
//            }
//        }
//    }
//
//    dismissLoading = { lifeScope ->
//        lifeScope.launch {
//            dialog?.dismissAllowingStateLoss()
//        }
//    }

    return this
}

/***
 * 请求数据
 * 请求的数据是BaseData<T>这样的格式，把BaseData<T> 转换成Resource<T>返回个调用方
 * Resource中包含success、loading、failure这些状态
 */
inline fun <reified S, reified T> requestBase(
    noinline call: suspend (S.() -> Call<BaseData<T>>),
    noinline builder: (RequestBuilder<S, BaseData<T>, T>.() -> Unit)? = null
) = requestBase(S::class.java, call = call, builder = builder)

/***
 * 请求数据
 * 请求的数据是BaseData<T>这样的格式，把BaseData<T> 转换成Resource<T>返回给调用方
 * Resource中包含success、loading、failure这些状态
 *
 *
 * 网络请求的具体实现是写在net模块中的，设计上，net模块应该要跟业务分离，可以在其他项目直接拿来用，而不需要改net模块中的代码
 *
 * 在当前项目中，已经跟后台约定好，所有的接口返回的数据，都是[BaseData]结构
 * eg:在获取用户信息的接口中，返回的数据为： {"actionType":0,"code":200,"data":{"userId":7,"phone":"18883278692","nickname":"这是测试字符串","realname":"","gender":1,"avatar":"18883278692/1625814287430"},"message":"success"}
 *
 * net模块中为了返回success、loading、failure这些状态，在返回的数据之外添加了一层[Resource]
 * 如果我们不在这里对Net模块中返回的数据进行一次转换，那么在使用的时候就需要
 * loadUser().observer(this){
 *      when(it.state){
 *          ResourceState.Success->{
 *              //第一个data是[Resource]的
 *              //第二个data是[BaseData]的
 *              //这样使用起来太麻烦，但是设计上net模块是不知道BaseData的具体结构的，它没办法自己就处理好[BaseData]
 *              tvName.text = it.data.data?.nickname
 *          }
 *      }
 * }
 *
 * 所以需要现在这个方法，这里通过[toBaseRes]方法，把[callAwait]返回的数据转换成去掉[BaseData]这一层的[Resource]
 * 而且可以在这里对一些统一的toast进行处理
 */
fun <S, T> requestBase(
    cls: Class<S>,
    call: suspend (S.() -> Call<BaseData<T>>),
    builder: (RequestBuilder<S, BaseData<T>, T>.() -> Unit)? = null
) = RequestEt.request<S, BaseData<T>, T>(
    createService = { type, baseUrl -> RetrofitFactory.create(cls, type, baseUrl) },
    call = { call(this).callAwait() },
    mapResult = { params -> toBaseRes(params) }
) {
    builder?.invoke(baseBuild())
}

inline fun <reified S, reified T> requestBaseByFlow(
    noinline call: suspend (S.() -> Call<BaseData<T>>),
    noinline builder: (RequestBuilder<S, BaseData<T>, T>.() -> Unit)? = null
) = requestBaseByFlow(S::class.java, call = call, builder = builder)

fun <S, T> requestBaseByFlow(
    cls: Class<S>,
    call: suspend (S.() -> Call<BaseData<T>>),
    builder: (RequestBuilder<S, BaseData<T>, T>.() -> Unit)? = null
) = RequestEt.requestByFlow<S, BaseData<T>, T>(
    createService = { type, baseUrl -> RetrofitFactory.create(cls, type, baseUrl) },
    call = { call(this).callAwait() },
    mapResult = { params -> toBaseRes(params) }
) {
    builder?.invoke(baseBuild())
}


