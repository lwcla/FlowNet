package cn.cla.library.net.entity

import cn.cla.library.net.utils.gsonFactory
import okhttp3.MediaType
import okhttp3.RequestBody
import org.json.JSONException
import org.json.JSONObject

class BodyBuilder {
    private val jsonObject = JSONObject()

    fun put(key: String, value: Any): BodyBuilder {
        try {
            jsonObject.put(key, value)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return this
    }


    fun build(): RequestBody {
        return RequestBody.create(MediaType.parse("application/json"), jsonObject.toString())
    }

    companion object {
        fun build(vararg pairs: Pair<String, Any?>): RequestBody {
            val map = mutableMapOf(*pairs).filterNot { it.value == null }
            return RequestBody.create(MediaType.parse("application/json"), gsonFactory.toJson(map))
        }
    }
}