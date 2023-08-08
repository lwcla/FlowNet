package cn.cla.net.demo.entity

import cn.cla.library.net.entity.PageCacheInf

/**
 * 分页加载
 *
 * @property pageIndex
 * @property pageSize
 * @property list
 * @constructor Create empty Page entity cache
 */
data class PageEntity(
    var pageIndex: Int,
    var pageSize: Int,
    var list: List<String>
) : PageCacheInf<PageEntity> {

    override fun pageCache(cache: PageEntity): PageEntity {
        val newList = mutableListOf<String>()
        newList.addAll(cache.list)
        newList.addAll(list)
        return PageEntity(pageIndex = pageIndex, pageSize = pageSize, list = newList)
    }
}