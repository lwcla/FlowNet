package cn.cla.library.net.vm

import cn.cla.library.net.entity.Resource

internal interface ObserverResultInf<T> {

    /** 设置调用的结果 */
    fun setResult(res: Resource<T>)

}