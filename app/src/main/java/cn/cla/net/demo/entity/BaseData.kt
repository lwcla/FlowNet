package cn.cla.net.demo.entity

/**
 * Created by midFang on 2021/6/22.
 * Useful:
 */
data class BaseData<T>(
    val errorMsg: String?,
    val errorCode: Int,
    val data: T?,
) {
    val dataSuc: Boolean
        get() = errorCode == 0 || errorCode == 200 || errorCode == 201
}
