package cn.cla.library.net.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

object NetConfig {
    internal var netImpl: INetProvider? = null

    val scope = CoroutineScope(Job())

    fun init(net: INetProvider) {
        netImpl = net
    }
}




