package cn.cla.library.net.interceptor

import cn.cla.library.net.config.INetHelper
import cn.cla.library.net.utils.logE
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.locks.ReentrantLock

/**
 * @author
 * @date  2020/4/12 1:02 PM
 * @version 1.0
 * token失效拦截处理
 */
internal class TokenInterceptor : Interceptor {

    private val refreshingTokenLocke = ReentrantLock()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalResponse = chain.proceed(originalRequest)
        val originalUrl = originalRequest.url().toString()


        val ignore = INetHelper.ignoreUrlWhenCheckToken().find { originalUrl.contains(it) }
        //不检查token是否过期
        if (ignore != null) {
            return originalResponse
        }

        if (INetHelper.isTokenOver(originalResponse)) {
            // 以下刷新token的逻辑
            refreshingTokenLocke.lock()
            val token = INetHelper.refreshToken()
            val newToken = token.newToken
            var message = token.message

            if (newToken.isNotEmpty()) {
                // 重新执行上次请求
                val newRequest = chain.request().newBuilder().header("token", newToken).build()
                originalResponse.body()!!.close()
                val newResp = chain.proceed(newRequest)
                refreshingTokenLocke.unlock()
                return newResp
            } else {
                if (message.isNullOrEmpty()) {
                    message = "鉴权信息已失效，请重新登录"
                }

                logE("刷新token失败，返回登录界面")
                INetHelper.tokenFail(message)
                refreshingTokenLocke.unlock()
                throw TokenFailureException()
            }
        }
        return originalResponse
    }
}

class TokenFailureException : RuntimeException("鉴权信息已失效，请重新登录")