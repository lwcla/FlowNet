package com.cla.adapter.library.holder

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.SparseArray
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.cla.adapter.library.ClaBaseAdapter
import com.cla.adapter.library.PreLoadBuilder
import com.cla.adapter.library.clickDebounce
import com.google.android.flexbox.FlexboxLayoutManager

abstract class ClaBaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {

    internal var adapter: ClaBaseAdapter<T>? = null
    val curRv get() = adapter?.recyclerView

    val map = SparseArray<View>()

    var bean: T? = null
    var pos: Int? = null

    abstract fun bind(baseAdapter: ClaBaseAdapter<T>, t: T, position: Int, payload: String? = null)

    inline fun <reified R : T> realBean(block: R.() -> Unit = {}) = (bean as? R?)?.apply(block)

    inline fun <reified V : View> getView(@IdRes id: Int): V = getViewOrNull(id)!!

    inline fun <reified V : View> getViewOrNull(@IdRes id: Int): V? = map[id]?.run { this as? V } ?: synchronized(map) {
        itemView.findViewById<V>(id)?.also {
            map[id] = it
        }
    }

    inline fun <reified V : View> View.covert(): V = this as V

    fun setText(@IdRes id: Int, textRes: Int) {
        val text = kotlin.runCatching { adapter!!.context.getString(textRes) }.getOrElse {
            it.printStackTrace()
            ""
        }
        setText(id, text)
    }

    fun setText(@IdRes id: Int, text: String?) {
        getView<TextView>(id).text = text
    }

    fun setImageDrawable(@IdRes id: Int, drawable: Drawable?) {
        getView<ImageView>(id).setImageDrawable(drawable)
    }

    fun setImageResource(@IdRes id: Int, @DrawableRes drawableRes: Int) {
        getView<ImageView>(id).setImageResource(drawableRes)
    }

    fun show(@IdRes id: Int, show: Boolean = true, hideValue: Int = View.GONE) {
        getView<View>(id).visibility = if (show) View.VISIBLE else hideValue
    }

    inline fun <reified V : View> clickBean(
        @IdRes id: Int,
        debounce: Boolean = true,
        debounceTime: Long = 600,
        crossinline clickListener: T.() -> Unit
    ) {
        getView<V>(id).clickBean(debounce, debounceTime, clickListener)
    }

    inline fun View.clickBean(
        debounce: Boolean = true,
        debounceTime: Long = 600,
        crossinline clickListener: T.() -> Unit
    ) {
        if (debounce && debounceTime > 0) {
            clickDebounce {
                bean?.apply { clickListener(this) }
            }
        } else {
            setOnClickListener {
                bean?.apply { clickListener(this) }
            }
        }
    }

    inline fun <reified V : View, reified R : T> clickRealBean(
        @IdRes id: Int,
        debounce: Boolean = true,
        debounceTime: Long = 600,
        crossinline clickListener: R.() -> Unit
    ) {
        getView<V>(id).clickRealBean<R>(debounce, debounceTime, clickListener)
    }

    inline fun <reified R : T> View.clickRealBean(
        debounce: Boolean = true,
        debounceTime: Long = 600,
        crossinline clickListener: R.() -> Unit
    ) {
        if (debounce && debounceTime > 0) {
            clickDebounce {
                (bean as? R?)?.let(clickListener)
            }
        } else {
            setOnClickListener {
                (bean as? R?)?.let(clickListener)
            }
        }
    }

    inline fun <reified V : View, reified R : T> longClickBean(
        @IdRes id: Int,
        crossinline listener: R.() -> Boolean
    ) {
        getView<V>(id).longClickBean<R>(listener)
    }

    inline fun <reified R : T> View.longClickBean(
        crossinline listener: R.() -> Boolean
    ) {
        setOnLongClickListener {
            (bean as? R?)?.let { listener(it) } ?: false
        }
    }

    fun addChildClickListener(viewId: Int) {
        addChildClickListener(getView<View>(viewId))
    }

    fun addChildClickListener(view: View) {
        view.setOnClickListener {
            val p = pos ?: return@setOnClickListener
            val b = bean ?: return@setOnClickListener
            if (p < 0) {
                return@setOnClickListener
            }

            adapter?.itemChildClickListener?.invoke(it, p, b)
        }
    }

    fun addChildLongClickListener(viewId: Int) {
        addChildLongClickListener(getView<View>(viewId))
    }

    fun addChildLongClickListener(view: View) {
        view.setOnLongClickListener { v ->
            val p = pos ?: return@setOnLongClickListener false
            val b = bean ?: return@setOnLongClickListener false
            if (p < 0) {
                return@setOnLongClickListener false
            }

            adapter?.itemChildLongClickListener?.invoke(v, p, b) ?: false
        }
    }

    /**
     *  https://www.jianshu.com/p/4f66c2c71d8c
     */
    open fun viewDetachedFromWindow() {}

    open fun viewAttachedToWindow() {}
}

/**
 * 当recyclerView的layoutManager设置成FlexboxLayoutManager时，不能直接设置LayoutParams，会直接崩溃
 * @constructor
 */
private class ContainerView(context: Context) : FrameLayout(context) {

    init {
        isFocusable = false
        isClickable = false
    }

    override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        var p = params
        if (p is FlexboxLayoutManager.LayoutParams) {
            //强制换行
            p.isWrapBefore = true
            //占满父布局
            p.flexBasisPercent = 1f
        } else {
            if (p == null) {
                p = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            } else {
                p.width = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }

        super.setLayoutParams(p)
    }
}

internal class LoadingViewHolder<T>(
    context: Context,
    private val builder: PreLoadBuilder,
    rootView: ViewGroup = ContainerView(context)
) : ClaBaseViewHolder<T>(rootView) {

    private val loadingView = builder.loadingV(context, builder)
    private val failedView = builder.failedV(context, builder)
    private val noMoreDataView = builder.noMoreDataV(context, builder)

    init {
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.CENTER

        (loadingView.parent as? ViewGroup?)?.removeView(loadingView)
        (failedView.parent as? ViewGroup?)?.removeView(failedView)
        (noMoreDataView.parent as? ViewGroup?)?.removeView(noMoreDataView)

        rootView.addView(loadingView, params)
        rootView.addView(failedView, params)
        rootView.addView(noMoreDataView, params)

        loadingView.visibility = View.GONE
        failedView.visibility = View.GONE
        noMoreDataView.visibility = View.GONE
    }

    fun bind() {
        if (builder.isLoading) {
            loadingView.visibility = View.VISIBLE
            failedView.visibility = View.GONE
            noMoreDataView.visibility = View.GONE
            return
        }

        if (builder.isLoadFailed) {
            loadingView.visibility = View.GONE
            failedView.visibility = View.VISIBLE
            noMoreDataView.visibility = View.GONE
            return
        }

        if (builder.isNoMoreData) {
            loadingView.visibility = View.GONE
            failedView.visibility = View.GONE
            noMoreDataView.visibility = View.VISIBLE
            return
        }

        loadingView.visibility = View.GONE
        failedView.visibility = View.GONE
        noMoreDataView.visibility = View.GONE
    }

    override fun bind(baseAdapter: ClaBaseAdapter<T>, t: T, position: Int, payload: String?) {
        bind()
    }
}

internal abstract class ContainerViewHolder<T>(
    context: Context,
    private val containerView: ViewGroup = ContainerView(context)
) : ClaBaseViewHolder<T>(containerView) {

    private val childView: View?
        get() = containerView.children.firstOrNull()

    fun bind(view: View?) {
        if (childView == view) {
            return
        }

        containerView.removeAllViews()
        view?.let {
            (it.parent as? ViewGroup?)?.removeView(view)
            containerView.addView(it)
        }
    }

    override fun bind(baseAdapter: ClaBaseAdapter<T>, t: T, position: Int, payload: String?) {
        bind(childView)
    }
}

internal class HeaderHolder<T>(context: Context) : ContainerViewHolder<T>(context)
internal class FooterHolder<T>(context: Context) : ContainerViewHolder<T>(context)
internal class EmptyHolder<T>(context: Context) : ContainerViewHolder<T>(context)

class DefaultViewHolder<T>(context: Context, itemView: View = ContainerView(context)) : ClaBaseViewHolder<T>(itemView) {
    override fun bind(baseAdapter: ClaBaseAdapter<T>, t: T, position: Int, payload: String?) {

    }
}