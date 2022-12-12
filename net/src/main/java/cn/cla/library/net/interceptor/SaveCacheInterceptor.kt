package cn.cla.library.net.interceptor

import android.os.StatFs
import cn.cla.library.net.utils.getApp
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit

private const val DISK_CACHE_PERCENTAGE = 0.02
private const val MIN_DISK_CACHE_SIZE: Long = 10 * 1024 * 1024 // 10MB
private const val MAX_DISK_CACHE_SIZE: Long = 250 * 1024 * 1024 // 250MB

/**
 * http缓存文件路径
 */
internal val HTTP_CACHE_PATH: String = File(getApp().cacheDir, "responses")
    .apply { if (!exists()) mkdirs() }.absolutePath

/**
 * 网络缓存设置
 */
internal fun getNetCache(): Cache {
    val cacheDir = File(HTTP_CACHE_PATH)
    return Cache(cacheDir, calculateDiskCacheSize(cacheDir))
}

/**
 * 网络缓存的大小设置
 */
private fun calculateDiskCacheSize(cacheDirectory: File): Long {
    return try {
        val cacheDir = StatFs(cacheDirectory.absolutePath)
        val size =
            DISK_CACHE_PERCENTAGE * cacheDir.blockCountLong * cacheDir.blockSizeLong
        return size.toLong().coerceIn(MIN_DISK_CACHE_SIZE, MAX_DISK_CACHE_SIZE)
    } catch (_: Exception) {
        MIN_DISK_CACHE_SIZE
    }
}

/**
 * 只读取网络数据
 */
internal class OnlyNetInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        //从网络读取
        request = request.newBuilder()
            .cacheControl(CacheControl.Builder().noCache().noStore().build())
            .build()

        return chain.proceed(request)
    }
}

/**
 * 只读取网络，但是返回的数据需要保存
 */
internal class OnlyNetRequestButSaveCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        //从网络读取
        request = request.newBuilder()
            .cacheControl(CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build())
            .build()

        return chain.proceed(request)
    }
}

/**
 * 先读取网络数据，如果网络数据读取失败，再去读取本地缓存
 */
internal class NetCacheInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {

        var request = chain.request()
        //从网络读取
        request = request.newBuilder()
            .cacheControl(CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build())
            .build()

        var originalResponse: Response? = null

        try {
            originalResponse = chain.proceed(request)
        } catch (e: Exception) {
            e.printStackTrace()

            //虽然有网络，但是网络连接失败的情况下，去读取缓存
            originalResponse?.body()?.close()
            request = request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build()
            return chain.proceed(request)
        }

        return originalResponse
    }
}

/**
 * 只读取缓存数据
 */
internal class ForceCacheInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        //强制读取缓存
        request = request.newBuilder()
            .removeHeader("Pragma") //清除头信息，否则缓存可能会不起作用
            .removeHeader("Cache-Control")
            .cacheControl(CacheControl.FORCE_CACHE)
            .build()

        return chain.proceed(request)
    }
}

/**
 * 缓存拦截器
 *
 * 先取缓存中的数据，同时从服务器获取数据
 * 拿到服务器的数据之后，更新缓存，更新ui
 */
internal class SaveCacheInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {

        //拿到请求
        val request = chain.request()
        val response = chain.proceed(request)
        //服务端设置的缓存策略
        val serviceCache = response.header("Cache-Control")
        if (serviceCache.isNullOrEmpty()) {
            val cacheControl = request.cacheControl().toString()
            return if (cacheControl.isEmpty()) {
                //网络数据覆盖缓存
                response.newBuilder()
                    .removeHeader("Pragma") //清除头信息，否则缓存可能会不起作用
                    .header("Cache-Control", "public, max-age=0") //重新设置缓存策略
                    .build()
            } else {
                response.newBuilder()
                    .removeHeader("Pragma") //清除头信息，否则缓存可能会不起作用
                    .header("Cache-Control", cacheControl) //重新设置缓存策略
                    .build()
            }
        }

        return response
    }
}


