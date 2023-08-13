package com.cla.adapter.library

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.cla.adapter.library.holder.ClaBaseViewHolder
import com.cla.adapter.library.holder.DefaultViewHolder
import com.cla.adapter.library.holder.EmptyHolder
import com.cla.adapter.library.holder.FooterHolder
import com.cla.adapter.library.holder.HeaderHolder
import com.cla.adapter.library.holder.LoadingViewHolder
import java.util.Arrays
import java.util.concurrent.Executors

abstract class ClaBaseAdapter<T>(
    val context: Context,
) : RecyclerView.Adapter<ClaBaseViewHolder<T>>() {

    companion object {
        internal const val LOADING_VIEW = Int.MIN_VALUE
        internal const val HEADER_VIEW = Int.MIN_VALUE + 1
        internal const val FOOTER_VIEW = Int.MIN_VALUE + 2
        internal const val EMPTY_VIEW = Int.MIN_VALUE + 3

        const val REFRESH_ADAPTER_HEADER = "refresh_adapter_header"
        const val REFRESH_ADAPTER_FOOTER = "refresh_adapter_footer"
        const val REFRESH_ADAPTER_PRE_LOAD = "refresh_adapter_pre_load"
        const val REFRESH_ADAPTER_EMPTY = "refresh_adapter_empty"

        private var builder = PreLoadBuilder()
        fun build(block: PreLoadBuilder.() -> Unit) {
            block(builder)
        }
    }

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT
    }

    private val delegateList by lazy {
        addDelegate()?.mapIndexed { index, delegate ->
            delegate.context = context
            delegate.inflater = inflater

            Pair(index, delegate)
        }
    }

    private val myHandler = ClaBaseAdapterHandler(this)
    private val singleThread = Executors.newSingleThreadExecutor()

    /** 在第一次装载adapter时，如果保存了之前的列表，那么[refreshData]之后，会恢复RecyclerView的状态 */
    internal val restoreState = lazy {
        stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    internal var recyclerView: RecyclerView? = null
    val curRv: () -> RecyclerView? = { recyclerView }

    /** 是否向 [dataList] 中添加过数据，避免一开始就显示emptyView */
    internal var hasSetListData: Boolean = false

    val inflater by lazy { LayoutInflater.from(context) }

    /**
     * [showDataList] [showDataSize] 是表示adapter中当前显示的数据集合和数量
     * 所有adapter元素的操作都是在handler的线程中执行的
     * 这就会有一定的延迟
     * 如果调refreshData()方法去刷新列表，然后马上想要拿到设置的数据集合的数量，应该是取[dataSize]的值
     */
    val showDataList = mutableListOf<T>()
    val showDataSize get() = showDataList.size

    /**
     * [dataList] [dataSize] 是使用者对adapter之后的数据集合和数量，但是这个时候这些数据并不一定已经显示在列表中了
     */
    val dataList = mutableListOf<T>()
    val dataSize get() = dataList.size

    /** 预加载 */
    internal var preLoadBuilder = builder.copy().also { it.showLoading = { loading() } }
    internal val needShowPreView get() = preLoadBuilder.preloadEnable && !preLoadBuilder.preloadClose
    internal val loadHolderPos get() = maxOf(itemCount - 1, 0)

    // *****************************headerView/footView/emptyView**************************************************
    internal var _showHeaderView: Boolean = false
    internal var _headerView: View? = null
    internal var _showFooterView: Boolean = false
    internal var _footerView: View? = null

    /**
     * 是否显示headerView，默认为true
     * [showHeaderView]==true && [headerView]!=null的情况下才会显示headerView
     * headerView的显示并不受[showDataSize]影响，即使[showDataSize]==0,也还是会显示headerView
     */
    var showHeaderView: Boolean
        get() = _showHeaderView
        set(value) {
            singleThread.execute {
                if (value == _showHeaderView) {
                    return@execute
                }

                myHandler.removeMessages(ClaBaseAdapterHandler.REFRESH_SHOW_HEADER_VIEW)
                val msg = myHandler.obtainMessage()
                msg.what = ClaBaseAdapterHandler.REFRESH_SHOW_HEADER_VIEW
                msg.obj = value
                myHandler.sendMessage(msg)
            }
        }

    /**
     * headerView
     * [showHeaderView]==true && [headerView]!=null的情况下才会显示headerView
     * 重复设置同一个view，不会刷新item
     */
    var headerView: View?
        get() = _headerView
        set(value) {
            singleThread.execute {
                if (value == _headerView) {
                    return@execute
                }
                myHandler.removeMessages(ClaBaseAdapterHandler.REFRESH_HEADER_VIEW)
                val msg = myHandler.obtainMessage()
                msg.what = ClaBaseAdapterHandler.REFRESH_HEADER_VIEW
                msg.obj = value
                myHandler.sendMessage(msg)
            }
        }

    /**
     * 是否显示footerView，默认为true
     * [showFooterView]==true && [footerView]!=null的情况下才会显示footerView
     * footerView 的显示并不受[showDataSize]影响，即使[showDataSize]==0,也还是会显示footerView
     */
    var showFooterView: Boolean
        get() = _showFooterView
        set(value) {
            singleThread.execute {
                if (value == _showFooterView) {
                    return@execute
                }
                myHandler.removeMessages(ClaBaseAdapterHandler.REFRESH_SHOW_FOOTER_VIEW)
                val msg = myHandler.obtainMessage()
                msg.what = ClaBaseAdapterHandler.REFRESH_SHOW_FOOTER_VIEW
                msg.obj = value
                myHandler.sendMessage(msg)
            }
        }

    /**
     * footView
     * [showFooterView]==true && [footerView]!=null的情况下才会显示footerView
     * 重复设置同一个view，不会刷新item
     */
    var footerView: View?
        get() = _footerView
        set(value) {
            singleThread.execute {
                if (value == _footerView) {
                    return@execute
                }
                myHandler.removeMessages(ClaBaseAdapterHandler.REFRESH_FOOTER_VIEW)
                val msg = myHandler.obtainMessage()
                msg.what = ClaBaseAdapterHandler.REFRESH_FOOTER_VIEW
                msg.obj = value
                myHandler.sendMessage(msg)
            }
        }

    /**
     * 当数据为空时是否显示emptyView
     * [showEmptyView]==true && [emptyView]!=null &&[showDataSize]==0 && [hasSetListData]==true 的情况下才会显示emptyView
     */
    var showEmptyView: Boolean = true

    /**
     * emptyView
     * 当数据为空时显示的view
     * [showEmptyView]==true && [emptyView]!=null &&[showDataSize]==0 && [hasSetListData]==true 的情况下才会显示emptyView
     */
    var emptyView: View? = null

    internal val isShowHeader get() = headerView != null && showHeaderView
    private val isShowFooter get() = footerView != null && showFooterView
    private val isHasEmpty get() = emptyView != null && showEmptyView && hasSetListData
    internal val isShowEmpty get() = isHasEmpty && showDataSize == 0

    private val headerPos get() = 0
    private val footerPos get() = maxOf((itemCount - 1).run { if (needShowPreView) (this - 1) else this }, 0)
    private val emptyPos get() = if (isShowHeader) 1 else 0
    // *****************************headerView/footView/emptyView**************************************************

    // *************************************自定义事件处理***********************************************************
    internal var itemChildClickListener: ((View, Int, T) -> Unit)? = null
    internal var itemChildLongClickListener: ((View, Int, T) -> Boolean)? = null
    // *************************************自定义事件处理***********************************************************

    /** 设置预加载 */
    fun setOnLoadMoreListener(loadMore: () -> Unit) {
        setPreLoad { loadData = loadMore }
    }

    /** 设置预加载 */
    fun setPreLoad(builderPre: (PreLoadBuilder.() -> Unit)? = null) {
        preLoadBuilder.preloadEnable = true
        builderPre?.invoke(preLoadBuilder)
    }

    fun setItemChildClickListener(click: (View, Int, T) -> Unit) {
        itemChildClickListener = click
    }

    fun setItemChildLongClickListener(click: (View, Int, T) -> Boolean) {
        itemChildLongClickListener = click
    }

    /**
     * 因为加上了headerView，所以数据集合中的位置和该数据在adapter中的位置并不相等
     * @param posFromAdapter 该数据在adapter中的位置
     * @return 该数据在集合中的位置
     */
    fun dataPos(posFromAdapter: Int): Int {
        if (isShowHeader) {
            return posFromAdapter - 1
        }

        return posFromAdapter
    }

    /**
     * 因为加上了headerView，所以数据集合中的位置和该数据在adapter中的位置并不相等
     *
     * @param posFromData 该数据在集合中的位置
     * @return 该数据在adapter中的位置
     */
    fun adapterPos(posFromData: Int): Int {
        if (isShowHeader) {
            return posFromData + 1
        }
        return posFromData
    }

    fun scrollToPosition(pos: Int) {
        singleThread.execute {
            val msg = myHandler.obtainMessage()
            msg.what = ClaBaseAdapterHandler.SCROLL_TO_POSITION
            msg.arg1 = pos
            myHandler.sendMessage(msg)
        }
    }

    /** 判断pos的位置是否为headerHolder */
    fun isHeaderHolder(pos: Int) = isShowHeader && pos == headerPos

    /** 判断pos的位置是否为footerHolder */
    fun isFooterHolder(pos: Int) = isShowFooter && pos == footerPos

    /** 判断pos的位置是否为loadHolder */
    fun isLoadHolder(pos: Int) = needShowPreView && pos == loadHolderPos

    /** 判断pos的位置是否为emptyHolder */
    fun isEmptyHolder(pos: Int) = isShowEmpty && pos == emptyPos

    open fun refreshData(list: List<T>) {
        refreshData(list, scrollToTop = false)
    }

    open fun refreshData(list: List<T>, scrollToTop: Boolean) {
        refreshData(list, scrollToTop = scrollToTop, scrollToTopIncludeHeader = true)
    }

    open fun refreshData(list: List<T>, scrollToTop: Boolean, scrollToTopIncludeHeader: Boolean) {
        refreshData(list, scrollToTop = scrollToTop, scrollToTopIncludeHeader = scrollToTopIncludeHeader, scrollToTopOffset = 0)
    }

    /**
     * 刷新数据
     * adapter中添加了headerView和footerView,如果headerView是webView，那么notifyDataSetChanged会导致WebView闪屏
     * 所以这个方法只会刷新数据的item，headerView和footerView是不会被刷新的
     * 如果需要刷新headerView或者footerView，请设置[headerView] [footerView]
     *
     * 注意：刷新的时候会从列表从后往前删除多余的数据，然后在刷新列表前面的item，这样列表就可能不会自动滚到第一个的位置
     *
     * @param scrollToTopIncludeHeader 滚动到顶部的时候，是否包含headerView的位置
     * @param scrollToTopOffset 滚动到顶部的偏移量
     */
    open fun refreshData(list: List<T>, scrollToTop: Boolean, scrollToTopIncludeHeader: Boolean, scrollToTopOffset: Int) {
        singleThread.execute {
            if (System.identityHashCode(dataList) != System.identityHashCode(list)) {
                dataList.clear()
                dataList.addAll(list)
            }
            hasSetListData = true

            // 刷新数据的时候移除其他关于数据的消息
            // headerView和footerView的消息跟数据是并行的，不能在这里把它们俩的消息也清空掉了
            myHandler.removeMessages(ClaBaseAdapterHandler.REFRESH_DATA)
            myHandler.removeMessages(ClaBaseAdapterHandler.ADD_DATA)
            myHandler.removeMessages(ClaBaseAdapterHandler.REMOVE_DATA)
            myHandler.removeMessages(ClaBaseAdapterHandler.REFRESH_ITEM)
            myHandler.removeMessages(ClaBaseAdapterHandler.REFRESH_ITEMS)
            myHandler.removeMessages(ClaBaseAdapterHandler.REFRESH_PRE_FAILED)
            myHandler.removeMessages(ClaBaseAdapterHandler.REFRESH_PRE_NO_MORE)
            myHandler.removeMessages(ClaBaseAdapterHandler.REFRESH_PRE_LOADING)
            myHandler.removeMessages(ClaBaseAdapterHandler.SCROLL_TO_POSITION)
            myHandler.removeMessages(ClaBaseAdapterHandler.CLOSE_PRE_LOAD)
            myHandler.removeMessages(ClaBaseAdapterHandler.REPLACE_ITEMS)

            val msg = myHandler.obtainMessage()
            msg.what = ClaBaseAdapterHandler.REFRESH_DATA
            msg.obj = AdapterRefreshData(list, scrollToTop, scrollToTopIncludeHeader, scrollToTopOffset)
            myHandler.sendMessage(msg)
        }
    }

    /**
     * 刷新当前所有的items
     * 这个时候不能直接refreshData(dataList)这样调用，[refreshData]会重置当前的预加载状态，本来已经是最后一页的数据，还要重新加载一次
     */
    open fun refreshAllItems(payload: String? = null) {
        singleThread.execute {
            refreshItems(0, dataSize, payload)
        }
    }

    /** 添加数据 */
    open fun addData(t: T) {
        singleThread.execute {
            addData(listOf(t), dataSize)
        }
    }

    /** 添加数据 */
    open fun addData(list: List<T>) {
        singleThread.execute {
            addData(list, dataSize)
        }
    }

    /** 添加数据 */
    open fun addData(t: T, index: Int) {
        singleThread.execute {
            addData(listOf(t), index)
        }
    }

    /** 添加数据 */
    open fun addData(list: List<T>, index: Int) {
        singleThread.execute {
            if (dataList.isEmpty()) {
                refreshData(list)
                return@execute
            }

            val addToEnd = if (index == dataSize) 1 else 0
            dataList.addAll(index, list)
            hasSetListData = true

            val msg = myHandler.obtainMessage()
            msg.what = ClaBaseAdapterHandler.ADD_DATA
            msg.arg1 = index
            msg.arg2 = addToEnd
            msg.obj = list
            myHandler.sendMessage(msg)
        }
    }

    open fun removeData(data: T) {
        singleThread.execute {
            val result = dataList.remove(data)
            if (!result) {
                return@execute
            }

            val msg = myHandler.obtainMessage()
            msg.what = ClaBaseAdapterHandler.REMOVE_DATA
            msg.obj = data
            myHandler.sendMessage(msg)
        }
    }

    /**
     * 删除数据
     */
    open fun removeData(pos: Int) {
        singleThread.execute {
            dataList.getOrNull(pos)?.let { removeData(it) }
        }
    }

    open fun refreshItem(pos: Int, payload: String? = null) {
        singleThread.execute {
            dataList.getOrNull(pos)?.let { refreshItem(it, payload) }
        }
    }

    open fun refreshItem(t: T, payload: String? = null) = try {
        singleThread.execute {
            val msg = myHandler.obtainMessage()
            msg.what = ClaBaseAdapterHandler.REFRESH_ITEM
            msg.obj = AdapterRefreshItem(t, payload)
            myHandler.sendMessage(msg)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    /**
     * 刷新items
     *
     * @param dataList List<T>
     * @param payload String?
     * @param successive dataList是否是一个adapter中连续的数据集合
     */
    open fun refreshItems(dataList: List<T>, payload: String? = null, successive: Boolean = true) {
        singleThread.execute {
            if (successive) {
                // dataList是一个adapter中连续的数据集合，而这个方法最终调用的是 notifyItemRangeChanged(pos, count, payload)方法去刷新
                dataList.firstOrNull()?.let { refreshItems(it, dataList.size, payload) }
            } else {
                dataList.forEach { refreshItem(it, payload) }
            }
        }
    }

    /**
     * 刷新items
     * @param pos Int
     * @param count Int
     * @param payload String?
     */
    open fun refreshItems(pos: Int, count: Int, payload: String? = null) {
        singleThread.execute {
            dataList.getOrNull(pos)?.let { refreshItems(it, count, payload) }
        }
    }

    /**
     * 刷新items
     * @param startData T
     * @param count Int
     * @param payload String?
     */
    open fun refreshItems(startData: T, count: Int, payload: String? = null) {
        if (count <= 0) {
            return
        }

        singleThread.execute {
            val msg = myHandler.obtainMessage()
            msg.what = ClaBaseAdapterHandler.REFRESH_ITEMS
            msg.obj = AdapterRefreshItems(startData, count, payload)
            myHandler.sendMessage(msg)
        }
    }

    /**
     * 替换item
     * @param pos 替换的开始位置
     * @param t 替换的数据
     * @param payload String?
     */
    open fun replaceItem(pos: Int, t: T, payload: String? = null) {
        singleThread.execute {
            replaceItems(pos, listOf(t), payload)
        }
    }

    /**
     * 替换items 只能替换连续的集合，如果是不连续的，那就只能一个一个的设置了
     * @param pos 替换的开始位置
     * @param newList 替换的数据
     * @param payload String?
     */
    open fun replaceItems(pos: Int, newList: List<T>, payload: String? = null) {
        singleThread.execute {
            if (dataList.isEmpty()) {
                return@execute
            }

            if (System.identityHashCode(dataList) != System.identityHashCode(newList)) {
                val removeList = dataList.filterIndexed { index, t ->
                    index >= pos && index < pos + newList.size
                }
                dataList.removeAll(removeList)
                if (dataList.lastIndex < pos) {
                    dataList.addAll(newList)
                } else {
                    dataList.addAll(pos, newList)
                }
            }

            val msg = myHandler.obtainMessage()
            msg.what = ClaBaseAdapterHandler.REPLACE_ITEMS
            msg.obj = AdapterReplaceItems(pos, newList, payload)
            myHandler.sendMessage(msg)
        }
    }

    /** 关闭预加载，刷新数据之后，会被重新打开 */
    fun closePreLoad() {
        singleThread.execute {
            if (!needShowPreView) {
                return@execute
            }

            // 表示已经在显示预加载的布局了，拦截[preload]方法的重复调用
            preLoadBuilder.showPreView()

            myHandler.removePreLoadMsg()
            val msg = myHandler.obtainMessage()
            msg.what = ClaBaseAdapterHandler.CLOSE_PRE_LOAD
            myHandler.sendMessage(msg)
        }
    }

    fun loadFailed() {
        singleThread.execute {
            if (!needShowPreView) {
                return@execute
            }

            // 表示已经在显示预加载的布局了，拦截[preload]方法的重复调用
            preLoadBuilder.showPreView()

            myHandler.removePreLoadMsg()
            val msg = myHandler.obtainMessage()
            msg.what = ClaBaseAdapterHandler.REFRESH_PRE_FAILED
            myHandler.sendMessage(msg)
        }
    }

    fun noMoreData() {
        singleThread.execute {
            if (!needShowPreView) {
                return@execute
            }

            preLoadBuilder.showPreView()

            myHandler.removePreLoadMsg()
            val msg = myHandler.obtainMessage()
            msg.what = ClaBaseAdapterHandler.REFRESH_PRE_NO_MORE
            myHandler.sendMessage(msg)
        }
    }

    fun loading() {
        singleThread.execute {
            if (!needShowPreView) {
                return@execute
            }

            preLoadBuilder.showPreView()

            myHandler.removePreLoadMsg()
            val msg = myHandler.obtainMessage()
            msg.what = ClaBaseAdapterHandler.REFRESH_PRE_LOADING
            myHandler.sendMessage(msg)
        }
    }

    /**
     * 根据位置和id找到控件
     * @param pos 在dataList中的位置
     * @param viewId 控件id
     * @return View?
     */
    fun getViewByPosition(pos: Int, @IdRes viewId: Int): View? {
        val recyclerView = recyclerView ?: return null
        val holder = recyclerView.findViewHolderForAdapterPosition(adapterPos(pos))
        val viewHolder = holder as ClaBaseViewHolder<*>? ?: return null
        return viewHolder.getViewOrNull(viewId)
    }

    /** 设置headerView */
    internal fun refreshHeaderView(originalPos: Int) {
        // adapter中是否已经有headerView
        val headerIsExists = originalPos >= 0
        if (headerIsExists) {
            if (isShowHeader) {
                // headerView本来就已经在adapter中了，这个时候只需要刷新
                notifyItemChanged(originalPos, REFRESH_ADAPTER_HEADER)
            } else {
                // 现在需要隐藏headerView，这个时候就从adapter中删除headerView
                notifyItemRemoved(originalPos)
                // 刷新之后的数据，避免数据错乱
                notifyVisibleItems(originalPos, showDataSize - originalPos, REFRESH_ADAPTER_HEADER)
            }
            return
        }

        if (isShowHeader) {
            val pos = headerPos
            notifyItemInserted(pos)
            // 刷新之后的数据，避免数据错乱
            notifyVisibleItems(originalPos, showDataSize - originalPos, REFRESH_ADAPTER_HEADER)
        }
    }

    /** 设置footerView */
    internal fun refreshFooterView(originalPos: Int) {
        // adapter中是否已经有footerView
        val footerIsExists = originalPos >= 0
        if (footerIsExists) {
            if (isShowFooter) {
                // footerView本来就已经在adapter中了，这个时候只需要刷新
                notifyItemChanged(originalPos, REFRESH_ADAPTER_FOOTER)
            } else {
                // 现在需要隐藏footerView，这个时候就从adapter中删除footerView
                notifyItemRemoved(originalPos)
                // 刷新之后的数据，避免数据错乱
                notifyItemChanged(originalPos)
                myHandler.notifyPreLoad()
            }
            return
        }

        if (isShowFooter) {
            val pos = footerPos
            notifyItemInserted(pos)
            // 刷新之后的数据，避免数据错乱
            // 在这里用notifyVisibleItems崩溃过，所以写成这样来刷新
            notifyItemChanged(pos)
            myHandler.notifyPreLoad()
        }
    }

    /**
     * 根据类型找到viewHolder的位置
     * @param type Int
     * @return Int
     */
    internal fun findPositionByType(type: Int): Int {
        if (type == EMPTY_VIEW) {
            val viewType = getItemViewType(emptyPos)
            if (viewType == type) {
                return emptyPos
            }
        }

        if (type == LOADING_VIEW) {
            val viewType = getItemViewType(loadHolderPos)
            if (viewType == type) {
                return loadHolderPos
            }
        }

        // itemCount并不是取的现在展示中的数据数量，而是直接调的getItemCount方法拿到的值
        if (type == FOOTER_VIEW) {
            val viewType = getItemViewType(footerPos)
            if (viewType == type) {
                return footerPos
            }
        }

        if (type == HEADER_VIEW) {
            val viewType = getItemViewType(headerPos)
            if (viewType == type) {
                return headerPos
            }
        }

        repeat(itemCount) {
            val viewType = getItemViewType(it)
            if (viewType == type) {
                return it
            }
        }
        return -1
    }

    internal fun scrollToPosWithOffset(pos: Int, offset: Int) {
        recyclerView?.layoutManager?.let { manager ->
            // 滚动到顶部的偏移量
            if (offset == 0) {
                manager.scrollToPosition(pos)
            } else {
                when (manager) {
                    is GridLayoutManager -> manager.scrollToPositionWithOffset(pos, offset)
                    is LinearLayoutManager -> manager.scrollToPositionWithOffset(pos, offset)
                    is StaggeredGridLayoutManager -> manager.scrollToPositionWithOffset(pos, offset)
                    else -> manager.scrollToPosition(pos)
                }
            }
        }
    }

    /**
     * 尽量只刷新可见范围中的数据
     *
     * @param pos 开始的位置
     * @param count 刷新数量
     * @param payload payload
     */
    internal fun notifyVisibleItems(pos: Int, count: Int, payload: String?) {
        if (count <= 0 || pos < 0) {
            return
        }

        val startPos = maxOf(pos, 0)

        val refreshPos: Int
        val refreshCount: Int

        when (val manager = curRv.invoke()?.layoutManager) {
            is GridLayoutManager -> {
                val beyondCount = manager.spanCount * 2
                refreshPos = maxOf(manager.findFirstVisibleItemPosition() - beyondCount, startPos)
                val lastPos = minOf(manager.findLastVisibleItemPosition() + beyondCount, showDataSize - 1)
                refreshCount = lastPos - refreshPos
            }

            is StaggeredGridLayoutManager -> {
                val beyondCount = manager.spanCount * 2
                val first = IntArray(manager.spanCount)
                val last = IntArray(manager.spanCount)

                manager.findFirstVisibleItemPositions(first)
                manager.findLastVisibleItemPositions(last)
                Arrays.sort(last)

                val firstVisiblePos = first.firstOrNull() ?: -1
                val lastVisiblePos = last.lastOrNull() ?: showDataSize

                refreshPos = maxOf(firstVisiblePos - beyondCount, startPos)
                val lastPos = minOf(lastVisiblePos + beyondCount, showDataSize - 1)
                refreshCount = lastPos - refreshPos
            }

            is LinearLayoutManager -> {
                val beyondCount = 4
                // 获取第一个可见view的位置
                refreshPos = maxOf(manager.findFirstVisibleItemPosition() - beyondCount, startPos)
                // 获取最后一个可见view的位置
                val lastPos = minOf(manager.findLastVisibleItemPosition() + beyondCount, showDataSize - 1)
                refreshCount = lastPos - refreshPos
            }

            else -> {
                refreshPos = maxOf(startPos, 0)
                refreshCount = minOf(count, showDataSize - refreshPos)
            }
        }

        if (refreshCount <= 0 || showDataSize <= 0) {
            return
        }

        notifyItemRangeChanged(refreshPos, refreshCount, payload)
    }

    /**
     * 判断是否进行预加载
     * @param position Int
     */
    private fun preload(position: Int) {
        if (!needShowPreView) {
            return
        }

        // 显示emptyView的时候也不要预加载
        if (showDataSize == 0) {
            return
        }

        if (preLoadBuilder.isShowing) {
            return
        }

        if (position < (showDataSize - preLoadBuilder.preloadItemCount).coerceAtLeast(0)) {
            // 索引值等于阈值
            return
        }

        loading()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClaBaseViewHolder<T> {
        if (viewType == HEADER_VIEW) {
            return HeaderHolder(context)
        }

        if (viewType == EMPTY_VIEW) {
            return EmptyHolder(context)
        }

        if (viewType == FOOTER_VIEW) {
            return FooterHolder(context)
        }

        if (viewType == LOADING_VIEW) {
            return LoadingViewHolder(context, builder = preLoadBuilder)
        }

        val delegate = delegateList?.findLast { it.first == viewType }
        delegate?.let { return it.second.createMyHolder(this, parent) }

        return convertHolder(this, parent)
    }

    override fun getItemViewType(position: Int): Int {
        if (position == 0 && isShowHeader) {
            return HEADER_VIEW
        }

        var dataSize = showDataSize
        if (isShowHeader) {
            dataSize++
        }

        if (isShowEmpty) {
            dataSize++
        }

        if (position == dataSize && isShowFooter) {
            return FOOTER_VIEW
        }

        if (position >= dataSize && needShowPreView) {
            return LOADING_VIEW
        }

        if (isShowEmpty) {
            return EMPTY_VIEW
        }

        val pos = dataPos(position)
        if (pos in 0 until showDataSize) {
            val data = showDataList[pos]
            val delegate = delegateList?.findLast { it.second.isForViewType(data, pos) }
            delegate?.let { return it.first }
        }

        return Int.MAX_VALUE
    }

    override fun getItemCount(): Int = showDataSize.run {
        if (isShowEmpty) this + 1 else this
    }.run {
        if (needShowPreView) this + 1 else this
    }.run {
        if (isShowHeader) this + 1 else this
    }.run {
        if (isShowFooter) this + 1 else this
    }

    override fun onBindViewHolder(holder: ClaBaseViewHolder<T>, position: Int) {
        if (holder is HeaderHolder) {
            holder.bind(headerView)
            return
        }

        if (holder is EmptyHolder) {
            holder.bind(emptyView)
            return
        }

        if (holder is FooterHolder) {
            holder.bind(footerView)
            return
        }

        if (holder is LoadingViewHolder) {
            holder.bind()
            // 点击重试时，需要再去加载数据
            preload(dataPos(position))
            return
        }

        val pos = dataPos(position)
        if (pos < 0 || pos >= showDataSize) {
            return
        }

        preload(pos)
        holder.bind(this, showDataList[pos], pos)
    }

    override fun onBindViewHolder(
        holder: ClaBaseViewHolder<T>,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isNullOrEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            val pos = dataPos(position)
            if (pos < 0 || pos >= showDataSize) {
                onBindViewHolder(holder, position)
                return
            }

            holder.bind(this, showDataList[pos], pos, payloads[0] as String)
        }
    }

    /**
     *  https://www.jianshu.com/p/4f66c2c71d8c
     */
    override fun onViewDetachedFromWindow(holder: ClaBaseViewHolder<T>) {
        holder.viewDetachedFromWindow()
    }

    override fun onViewAttachedToWindow(holder: ClaBaseViewHolder<T>) {
        holder.viewAttachedToWindow()
    }

    // https://www.jianshu.com/p/4f66c2c71d8c
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        myHandler.removeCallbacksAndMessages(null)
        this.recyclerView = null
    }

    /** 创建viewHolder */
    abstract fun convertHolder(adapter: ClaBaseAdapter<T>, parent: ViewGroup): ClaBaseViewHolder<T>

    /** 添加ItemViewDelegate集合 */
    abstract fun addDelegate(): List<ItemViewDelegate<T>>?

    /**
     * 用来生成viewHolder
     */
    abstract class ItemViewDelegate<T> {

        lateinit var context: Context
        lateinit var inflater: LayoutInflater

        final fun createMyHolder(adapter: ClaBaseAdapter<T>, parent: ViewGroup): ClaBaseViewHolder<T> {
            return convertHolder(adapter, parent)
        }

        /**
         * 判断当前是否为目标ViewHolder
         */
        abstract fun isForViewType(t: T, position: Int): Boolean

        /**
         * 生成viewHolder
         */
        abstract fun convertHolder(adapter: ClaBaseAdapter<T>, parent: ViewGroup): ClaBaseViewHolder<T>
    }
}

internal inline fun <T> createHolder(
    baseAdapter: ClaBaseAdapter<T>,
    @LayoutRes layoutRes: Int,
    parent: ViewGroup,
    inflater: LayoutInflater,
    crossinline initHolder: ClaBaseViewHolder<T>.() -> Unit,
    crossinline onViewDetachedFromWindow: (holder: ClaBaseViewHolder<T>) -> Unit,
    crossinline onViewAttachedToWindow: (holder: ClaBaseViewHolder<T>) -> Unit,
    crossinline bindData: ClaBaseViewHolder<T>.(T, Int, String?) -> Unit,
) = createHolder(
    baseAdapter = baseAdapter,
    view = inflater.inflate(layoutRes, parent, false),
    initHolder = initHolder,
    onViewDetachedFromWindow = onViewDetachedFromWindow,
    onViewAttachedToWindow = onViewAttachedToWindow,
    bindData = bindData,
)

internal inline fun <T> createHolder(
    baseAdapter: ClaBaseAdapter<T>,
    view: View,
    crossinline initHolder: ClaBaseViewHolder<T>.() -> Unit,
    crossinline onViewDetachedFromWindow: (holder: ClaBaseViewHolder<T>) -> Unit,
    crossinline onViewAttachedToWindow: (holder: ClaBaseViewHolder<T>) -> Unit,
    crossinline bindData: ClaBaseViewHolder<T>.(T, Int, String?) -> Unit,
) = object : ClaBaseViewHolder<T>(view) {

    init {
        this.adapter = baseAdapter
        initHolder()
    }

    override fun bind(baseAdapter: ClaBaseAdapter<T>, t: T, position: Int, payload: String?) {
        adapter = baseAdapter
        this.bean = t
        this.pos = position
        this.bindData(t, position, payload)
    }

    override fun viewDetachedFromWindow() {
        onViewDetachedFromWindow(this)
    }

    override fun viewAttachedToWindow() {
        onViewAttachedToWindow(this)
    }
}

// ************************************************item只有一种类型*******************************************************************
/**
 * 整个adapter中只有一个类型的item时，继承这个类
 * @property layoutRes itemView的xml，如果不传的话，那就必须重写[createItemView]方法
 */
abstract class SingleAdapterAbs<T>(
    context: Context,
    @LayoutRes private val layoutRes: Int? = null,
) : ClaBaseAdapter<T>(context) {

    override fun addDelegate() = null

    override fun convertHolder(
        adapter: ClaBaseAdapter<T>,
        parent: ViewGroup,
    ) = if (layoutRes != null) {
        createHolder<T>(
            baseAdapter = adapter,
            layoutRes = layoutRes,
            parent = parent,
            inflater = inflater,
            initHolder = { initHolder() },
            onViewDetachedFromWindow = { it.detachedFromWindow() },
            onViewAttachedToWindow = { it.attachedToWindow() },
        ) { bean, pos, payload ->
            bindHolder(bean, pos, payload)
        }
    } else {
        createHolder<T>(
            baseAdapter = adapter,
            view = createItemView()!!,
            initHolder = { initHolder() },
            onViewDetachedFromWindow = { it.detachedFromWindow() },
            onViewAttachedToWindow = { it.attachedToWindow() },
        ) { bean, pos, payload ->
            bindHolder(bean, pos, payload)
        }
    }

    open fun createItemView(): View? = null
    open fun ClaBaseViewHolder<T>.detachedFromWindow() {}
    open fun ClaBaseViewHolder<T>.attachedToWindow() {}

    abstract fun ClaBaseViewHolder<T>.initHolder()
    abstract fun ClaBaseViewHolder<T>.bindHolder(t: T, pos: Int, payload: String?)
}
// ************************************************item只有一种类型*******************************************************************

// *************************************************item有多种类型********************************************************************
abstract class MultiAdapterAbs<T>(context: Context) : ClaBaseAdapter<T>(context) {
    override fun convertHolder(adapter: ClaBaseAdapter<T>, parent: ViewGroup) = DefaultViewHolder<T>(context)
}

/**
 * 一个adapter中有多个类型的item时，继承这个类
 * @property layoutRes itemView的xml，如果不传的话，那就必须重写[createItemView]方法
 */
abstract class MultiAdapterDelegateAbs<T>(
    @LayoutRes private val layoutRes: Int? = null,
) : ClaBaseAdapter.ItemViewDelegate<T>() {

    override fun convertHolder(
        adapter: ClaBaseAdapter<T>,
        parent: ViewGroup,
    ) = if (layoutRes != null) {
        createHolder<T>(
            baseAdapter = adapter,
            layoutRes = layoutRes,
            parent = parent,
            inflater = inflater,
            initHolder = { initHolder() },
            onViewDetachedFromWindow = { it.detachedFromWindow() },
            onViewAttachedToWindow = { it.attachedToWindow() },
        ) { bean, pos, payload ->
            bindHolder(bean, pos, payload)
            View(parent.context)
        }
    } else {
        createHolder<T>(
            baseAdapter = adapter,
            view = createItemView()!!,
            initHolder = { initHolder() },
            onViewDetachedFromWindow = { it.detachedFromWindow() },
            onViewAttachedToWindow = { it.attachedToWindow() },
        ) { bean, pos, payload ->
            bindHolder(bean, pos, payload)
        }
    }

    open fun createItemView(): View? = null
    open fun ClaBaseViewHolder<T>.detachedFromWindow() {}
    open fun ClaBaseViewHolder<T>.attachedToWindow() {}

    abstract fun ClaBaseViewHolder<T>.initHolder()
    abstract fun ClaBaseViewHolder<T>.bindHolder(t: T, pos: Int, payload: String?)
}
// *************************************************item有多种类型********************************************************************
