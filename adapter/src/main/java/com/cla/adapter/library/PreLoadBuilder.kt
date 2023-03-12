package com.cla.adapter.library

import android.content.Context
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.ContentLoadingProgressBar


typealias PreloadCount = (Int) -> Int
typealias CreatePreloadView = (Context, PreLoadBuilder) -> View

/**
 * 预加载的数据类
 * @property loadingText 默认的LoadingView的文字提示
 * @property failText 默认的FailedView的文字提示
 * @property noMoreDataText 默认的NoMoreDataView的文字提示
 * @property preloadCount 手动设置预加载偏移量，当偏移量大于0时才会有效，否则会按照第一页数据的二分之一的偏移量开始加载下一页的数据
 * @property loadingView loadingView
 * @property failedView failedView
 * @property noMoreDataView noMoreDataView
 * @constructor
 */
data class PreLoadBuilder(
    var loadingText: String = "正在加载",
    var failText: String = "加载失败，点击重试",
    var noMoreDataText: String = "没有更多了",
    var preloadCount: PreloadCount? = null,
    var loadingView: CreatePreloadView? = null,
    var failedView: CreatePreloadView? = null,
    var noMoreDataView: CreatePreloadView? = null,
) {

    /**
     * 加载数据的方法
     */
    var loadData: (() -> Unit)? = null

    /**
     * 是否执行预加载策略
     */
    var preloadEnable: Boolean = false
        get() = field && loadData != null

    /**
     * 预加载是否被关闭，关闭之后刷新数据，预加载会被重新打开
     */
    var preloadClose: Boolean = false

    /**
     * 显示loadingView
     * 加载失败之后，点击重试的时候会调这个方法
     */
    var showLoading: (() -> Unit)? = null

    /**
     * 设置预加载偏移量，默认是到列表的二分之一的时候开始加载下一页的数据
     */
    internal var preloadItemCount: Int = -1

    internal var isLoading: Boolean = false
    internal var isLoadFailed: Boolean = false
    internal var isNoMoreData: Boolean = false

    /** 表示已经在显示预加载的布局了，拦截[MyBaseAdapter.preload]方法的重复调用 */
    internal var isShowing: Boolean = false

    //*******************************这一坨代码只是为了不重复创建对象，并没什么其他用处**********************************************************
    private val _preLoadCount: PreloadCount by lazy { { pageSize -> pageSize / 2 } }
    private val _loadingView: CreatePreloadView by lazy { { ctx, pre -> defaultLoadingView(ctx, pre) } }
    private val _failedView: CreatePreloadView by lazy { { ctx, pre -> defaultFailedView(ctx, pre) } }
    private val _noMoreDataView: CreatePreloadView by lazy { { ctx, pre -> defaultNoMoreDataView(ctx, pre) } }
    internal val preCount get() = preloadCount ?: _preLoadCount
    internal val loadingV get() = loadingView ?: _loadingView
    internal val failedV get() = failedView ?: _failedView
    internal val noMoreDataV get() = noMoreDataView ?: _noMoreDataView
    //*******************************这一坨代码只是为了不重复创建对象，并没什么其他用处**********************************************************

    internal fun close() {
        preloadClose = true
        reset()
    }

    internal fun reset() {
        isShowing = false
        isLoading = false
        isLoadFailed = false
        isNoMoreData = false
    }

    internal fun showPreView() {
        isShowing = true
    }

    internal fun loading() {
        isShowing = true
        isLoading = true
        isLoadFailed = false
        isNoMoreData = false
        loadData?.invoke()
    }

    internal fun loadFailed() {
        isShowing = true
        isLoading = false
        isLoadFailed = true
        isNoMoreData = false
    }

    internal fun noMoreData() {
        isShowing = true
        isLoading = false
        isLoadFailed = false
        isNoMoreData = true
    }
}

/**
 * 用来复制默认设置的参数
 * @receiver PreLoadBuilder
 * @return PreLoadBuilder
 */
internal fun PreLoadBuilder.copy() = PreLoadBuilder().also {
    it.loadingText = loadingText
    it.failText = failText
    it.noMoreDataText = noMoreDataText
    it.preloadCount = preloadCount
    it.loadingView = loadingView
    it.failedView = failedView
    it.noMoreDataView = noMoreDataView
}

/**
 * 默认的加载中布局
 * @param context Context
 * @return View
 */
private fun defaultLoadingView(
    context: Context,
    builderPre: PreLoadBuilder
) = LinearLayout(context).apply {

    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    gravity = Gravity.CENTER
    orientation = LinearLayout.HORIZONTAL

    val ctx = ContextThemeWrapper(context, android.R.style.Widget_Material_ProgressBar)
    val progressBar = ContentLoadingProgressBar(ctx).also {
        val size = 18.dp
        it.layoutParams = LinearLayout.LayoutParams(size, size)

        //这样设置的颜色才有效
        try {
            val c = ctx.colorValue(R.color.color_999999)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.indeterminateDrawable.colorFilter =
                    BlendModeColorFilter(c, BlendMode.SRC_ATOP)
            } else {
                it.indeterminateDrawable.setColorFilter(c, PorterDuff.Mode.SRC_ATOP)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        it.isClickable = false
        it.isFocusable = false
    }

    val textView = getTextView(context).also {
        it.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        it.text = builderPre.loadingText
    }

    addView(progressBar)
    addView(textView)
}

private fun defaultFailedView(
    context: Context,
    builderPre: PreLoadBuilder
) = getTextView(context).apply {
    if (builderPre.loadData != null) {
        isClickable = true
        isFocusable = true
    }

    clickDebounce {
        //showLoading中会调loadData
        builderPre.showLoading?.invoke()
    }
    text = builderPre.failText
}

private fun defaultNoMoreDataView(
    context: Context,
    builderPre: PreLoadBuilder
) = getTextView(context).apply {
    text = builderPre.noMoreDataText
}

private fun getTextView(context: Context) = AppCompatTextView(context).apply {

    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
    gravity = Gravity.CENTER
    val pad = 14.dp
    setPadding(pad, pad, pad, pad)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    setTextColor(context.colorValue(R.color.color_999999))
    maxLines = 1
    setLines(1)
    ellipsize = TextUtils.TruncateAt.END
}