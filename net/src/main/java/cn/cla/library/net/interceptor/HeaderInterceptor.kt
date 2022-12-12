package cn.cla.library.net.interceptor

import cn.cla.library.net.config.INetHelper
import okhttp3.Interceptor
import okhttp3.Response

/**
 * @author
 * @date  2020/4/12 1:02 PM
 * @version 1.0
 *      添加公共请求头
 */
class HeaderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val reqBuilder = originalRequest.newBuilder()

        INetHelper.addHeader().entries.forEach {
            reqBuilder.addHeader(it.key, it.value)
        }

        return chain.proceed(reqBuilder.build())
    }

}