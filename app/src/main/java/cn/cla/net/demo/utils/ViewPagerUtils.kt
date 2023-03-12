package cn.cla.net.demo.utils

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import cn.cla.net.demo.config.colorC1
import cn.cla.net.demo.config.colorC12
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator


//******************ViewPager2中的fragment数量比较多或者数量不确定时，用这个方法，需要动态去创建fragment***********************
data class BindTabLayoutParams(
    var resetTab: ((tabLayout: TabLayout) -> Unit)? = null
)

fun <T> ViewPager2.bindTabLayout(
    aty: FragmentActivity,
    tabLayout: TabLayout,
    tabShow: (tab: TabLayout.Tab, t: T) -> Unit,
    createFragment: (pos: Int, t: T) -> Fragment,
    build: (BindTabLayoutParams.() -> Unit)? = null
): ViewPager2Adapter<T> = bindTabLayout(aty, null, tabLayout, tabShow, createFragment, build)

fun <T> ViewPager2.bindTabLayout(
    fragment: Fragment,
    tabLayout: TabLayout,
    tabShow: (tab: TabLayout.Tab, t: T) -> Unit,
    createFragment: (pos: Int, t: T) -> Fragment,
    build: (BindTabLayoutParams.() -> Unit)? = null
): ViewPager2Adapter<T> = bindTabLayout(null, fragment, tabLayout, tabShow, createFragment, build)

fun <T> ViewPager2.bindTabLayout(
    aty: FragmentActivity?,
    fragment: Fragment?,
    tabLayout: TabLayout,
    tabShow: (tab: TabLayout.Tab, t: T) -> Unit,
    createFragment: (pos: Int, t: T) -> Fragment,
    build: (BindTabLayoutParams.() -> Unit)? = null
): ViewPager2Adapter<T> {

    val params = BindTabLayoutParams().apply { build?.invoke(this) }

    val showAdapter = if (aty != null) {
        ViewPager2Adapter(aty, this, createFragment)
    } else if (fragment != null) {
        ViewPager2Adapter(fragment, this, createFragment)
    } else {
        throw RuntimeException("aty和fragment不能同时为空")
    }

    tabLayout.also {
//        it.layoutParams = ViewGroup.LayoutParams(WRAP_CONTENT, dp44)
//        it.tabGravity = TabLayout.GRAVITY_FILL
        it.tabMode = TabLayout.MODE_SCROLLABLE
        it.setTabTextColors(colorC12, colorC1)
        it.setSelectedTabIndicatorColor(colorC1)
        it.tabIndicatorAnimationMode = TabLayout.INDICATOR_ANIMATION_MODE_ELASTIC
        it.isTabIndicatorFullWidth = false

        params.resetTab?.invoke(it)
    }

    adapter = showAdapter

    TabLayoutMediator(tabLayout, this, true, true) { tab, position ->
        tabShow(tab, showAdapter.dataList[position])
    }.attach()

    return showAdapter
}


class ViewPager2Adapter<T> : FragmentStateAdapter {

    private val viewPager2: ViewPager2
    private val createFragment: (pos: Int, t: T) -> Fragment

    constructor(
        activity: FragmentActivity,
        viewPager2: ViewPager2,
        createFragment: (pos: Int, t: T) -> Fragment
    ) : super(activity) {
        this.viewPager2 = viewPager2
        this.createFragment = createFragment
    }

    constructor(
        fragment: Fragment,
        viewPager2: ViewPager2,
        createFragment: (pos: Int, t: T) -> Fragment
    ) : super(fragment) {
        this.viewPager2 = viewPager2
        this.createFragment = createFragment
    }

    val dataList = mutableListOf<T>()

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData(list: List<T>, scrollToZero: Boolean = true) {
        if (System.identityHashCode(list) != System.identityHashCode(dataList)) {
            dataList.clear()
            dataList.addAll(list)
        }

        notifyDataSetChanged()
        if (scrollToZero) {
            viewPager2.currentItem = 0
        }
    }

    override fun getItemCount(): Int = dataList.size

    override fun createFragment(position: Int): Fragment {
        return createFragment(position, dataList[position])
    }
}
//******************ViewPager2中的fragment数量比较多或者数量不确定时，用这个方法，需要动态去创建fragment***********************

