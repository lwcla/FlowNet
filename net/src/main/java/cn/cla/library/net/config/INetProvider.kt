package cn.cla.library.net.config

import android.content.Context
import cn.cla.library.net.entity.RefreshTokenEntity
import cn.cla.library.net.utils.token
import okhttp3.Interceptor
import okhttp3.Response

interface INetProvider {

    /**
     * 当前使用的服务器地址
     * 这个地址是由baseUrl和port拼起来的
     */
    fun getBaseUrl(): String

    /**
     *  token过期的处理
     *  一般是回到登录页
     */
    fun tokenFail(msg: String)

    /** 请求头 */
    fun addHeader(): Map<String, String> = emptyMap()

    /** token是否过期 */
    fun isTokenOver(response: Response) = response.code() == 401 || response.code() == 403

    /** 清除本地token */
    fun clearToken(context: Context) {
        saveToken(context, "")
    }

    /** 获取token */
    fun getToken(context: Context): String = context.token

    /** 保存token */
    fun saveToken(context: Context, token: String) {
        context.token = token
    }

    /**
     * 有一些请求是不需要检查token是否过期的
     *  比如说登录，注册等，这个时候本来token就应该是空的
     */
    fun ignoreUrlWhenCheckToken(): List<String> = emptyList()

    /**
     * 刷新token
     * 请求过程中，如果token过期了，可以去调刷新token的方法，拿到新的token，之后继续刚才的请求
     * 对用户来说，就好像token从来没有过期一样
     */
    fun refreshToken(): RefreshTokenEntity = RefreshTokenEntity(200, "", null)

    /** 添加网络拦截器 */
    fun addInterceptor(): List<Interceptor> = emptyList()
}

object INetHelper : INetProvider {

    private val impl: INetProvider
        get() = NetConfig.netImpl ?: throw NullPointerException("没有找到INetProvider的实现类")

    override fun addHeader() = impl.addHeader()

    override fun getBaseUrl() = impl.getBaseUrl()

    override fun isTokenOver(response: Response) = impl.isTokenOver(response)

    override fun clearToken(context: Context) = impl.clearToken(context)

    override fun getToken(context: Context) = impl.getToken(context)

    override fun saveToken(context: Context, token: String) = impl.saveToken(context, token)

    override fun tokenFail(msg: String) = impl.tokenFail(msg)

    override fun refreshToken() = impl.refreshToken()

    override fun addInterceptor() = impl.addInterceptor()
}

