package cn.cla.library.net.entity

interface PageCacheInf<T> {
    fun pageCache(cache: T): T
}
