package cn.cla.library.net.interceptor

import okhttp3.*
import okhttp3.internal.http.HttpHeaders
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/**
 * @author
 * @version 1.0
 * @date 2020/4/12 11:33 AM
 */
internal class HttpLoggingInterceptor(tag: String) : Interceptor {
    @Volatile
    private var printLevel = PrintLevel.NONE
    private var colorLevel = Level.INFO
    private var logger: Logger = Logger.getLogger(tag)

    enum class PrintLevel {
        NONE,  //不打印log
        BASIC,  //只打印 请求首行 和 响应首行
        HEADERS,  //打印请求和响应的所有 Header
        BODY //所有数据全部打印
    }

    companion object {
        private val UTF8 = StandardCharsets.UTF_8
        private fun getCharset(contentType: MediaType?): Charset? {
            var charset = if (contentType != null) contentType.charset(UTF8) else UTF8
            if (charset == null) charset = UTF8
            return charset
        }

        /**
         * Returns true if the body in question probably contains human readable text. Uses a small sample
         * of code points to detect unicode control characters commonly used in binary file signatures.
         */
        private fun isPlaintext(mediaType: MediaType?): Boolean {
            if (mediaType == null) {
                return false
            }
            if ("text" == mediaType.type()) {
                return true
            }
            var subtype = mediaType.subtype()
            subtype = subtype.toLowerCase()
            return subtype.contains("x-www-form-urlencoded") || subtype.contains("json") || subtype.contains("xml") || subtype.contains("html")
        }
    }

    fun setPrintLevel(printLevel: PrintLevel?) {
        if (printLevel == null) {
            throw NullPointerException("level == null. Use Level.NONE instead.")
        }
        this.printLevel = printLevel
    }

    fun setLogLevel(level: Level) {
        colorLevel = level
    }

    private fun log(message: String) {
        logger.log(colorLevel, message)
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (printLevel == PrintLevel.NONE) {
            return chain.proceed(request)
        }
        //请求日志拦截
        logForRequest(request, chain.connection())
        //执行请求，计算请求时间
        val startNs = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            log("<-- HTTP FAILED: $e")
            throw e
        }
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        //响应日志拦截
        return logForResponse(request, response, tookMs)
    }

    @Throws(IOException::class)
    private fun logForRequest(request: Request, connection: Connection?) {
        val logBody = printLevel == PrintLevel.BODY
        val logHeaders = printLevel == PrintLevel.BODY || printLevel == PrintLevel.HEADERS
        val requestBody = request.body()
        val hasRequestBody = requestBody != null
        val protocol = if (connection != null) connection.protocol() else Protocol.HTTP_1_1
        try {
            val requestStartMessage = "--> ${request.method()} ${request.url()} $protocol"
            log(requestStartMessage)
            if (logHeaders) {
                if (hasRequestBody) {
                    // Request body headers are only present when installed as a network interceptor. Force
                    // them to be included (when available) so there values are known.
                    if (requestBody!!.contentType() != null) {
                        log("\tContent-Type: ${requestBody.contentType()}")
                    }
                    if (requestBody.contentLength() != -1L) {
                        log("\tContent-Length: ${requestBody.contentLength()}")
                    }
                }
                val headers = request.headers()
                var i = 0
                val count = headers.size()
                while (i < count) {
                    val name = headers.name(i)
                    // Skip headers from the request body as they are explicitly logged above.
                    if (!"Content-Type".equals(name, ignoreCase = true) && !"Content-Length".equals(name, ignoreCase = true)) {
                        log("\t" + name + ": " + headers.value(i))
                    }
                    i++
                }
                log(" ")
                if (logBody && hasRequestBody) {
                    if (isPlaintext(requestBody!!.contentType())) {
                        bodyToString(request)
                    } else {
                        log("\tbody: maybe [binary body], omitted!")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            log("--> END " + request.method())
        }
    }

    private fun logForResponse(request: Request, response: Response, tookMs: Long): Response {
        val builder = response.newBuilder()
        val clone = builder.build()
        var responseBody = clone.body()
        val logBody = printLevel == PrintLevel.BODY
        val logHeaders = printLevel == PrintLevel.BODY || printLevel == PrintLevel.HEADERS
        try {
            log("<-- ${clone.code()} ${clone.message()} ${clone.request().url()} (${tookMs}ms)")
            if (logHeaders) {
                val headers = clone.headers()
                var i = 0
                val count = headers.size()
                while (i < count) {
                    log("\t" + headers.name(i) + ": " + headers.value(i))
                    i++
                }
                log(" ")
                if (logBody && HttpHeaders.hasBody(clone)) {
                    if (responseBody == null) {
                        return response
                    }

                    if (isPlaintext(responseBody.contentType())) {
                        val bytes = readAllBytes(responseBody.byteStream())
                        val contentType = responseBody.contentType()
                        val body = String(bytes, getCharset(contentType)!!)
                        log("\tbody:$body <---> ${request.url()}")
//                        logI(body, tag = "responseBody", isJson = true)
                        responseBody = ResponseBody.create(responseBody.contentType(), bytes)
                        return response.newBuilder().body(responseBody).build()
                    } else {
                        log("\tbody: maybe [binary body], omitted!")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            log("<-- END HTTP")
        }
        return response
    }

    @Throws(IOException::class)
    fun readAllBytes(inputStream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        copyAllBytes(inputStream, out)
        return out.toByteArray()
    }

    /**
     * Copies all available data from in to out without closing any stream.
     *
     * @return number of bytes copied
     */
    @Throws(IOException::class)
    fun copyAllBytes(inputStream: InputStream, out: OutputStream): Int {
        var byteCount = 0
        val buffer = ByteArray(8192)
        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) {
                break
            }
            out.write(buffer, 0, read)
            byteCount += read
        }
        return byteCount
    }

    private fun bodyToString(request: Request) {
        try {
            val copy = request.newBuilder().build()
            val body = copy.body() ?: return
            val buffer = Buffer()
            body.writeTo(buffer)
            val charset = getCharset(body.contentType()) ?: return

            val content = buffer.readString(charset)

            log("\tbody:${content}")
//            logI(content, tag = "requestBody", isJson = true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}