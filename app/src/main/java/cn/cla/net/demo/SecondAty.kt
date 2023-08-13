package cn.cla.net.demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import cn.cla.library.net.entity.success
import cn.cla.library.net.vm.observe
import cn.cla.net.demo.config.dp
import cn.cla.net.demo.utils.bindTabLayout
import cn.cla.net.demo.utils.findView
import cn.cla.net.demo.utils.lazyNone
import cn.cla.net.demo.vm.MainVm
import com.cla.adapter.library.SingleAdapterAbs
import com.cla.adapter.library.holder.ClaBaseViewHolder
import com.google.android.material.tabs.TabLayout

class SecondAty : AppCompatActivity() {

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, SecondAty::class.java))
        }
    }

    private val tabLayout by findView<TabLayout>(R.id.tabLayout)
    private val viewPager by findView<ViewPager2>(R.id.viewPager)

    private val aty get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second_aty)

        val adapter = viewPager.bindTabLayout<TypeEntity>(
            aty,
            tabLayout,
            tabShow = { tab, t -> tab.text = t.name },
            createFragment = { _, t -> SecondFragment.newInstance(t.name) }
        )
        viewPager.offscreenPageLimit = 1

        val list = mutableListOf<TypeEntity>()
        repeat(30) {
            list.add(TypeEntity(it, "title${it}"))
        }
        adapter.refreshData(list)
    }
}


data class TypeEntity(val type: Int, val name: String)

class SecondFragment : Fragment() {

    companion object {
        private const val KEY_NAME = "key_name"
        fun newInstance(name: String) = SecondFragment().also {
            val bundle = Bundle()
            bundle.putString(KEY_NAME, name)
            it.arguments = bundle
        }
    }

    private val name by lazyNone { arguments?.getString(KEY_NAME) }

    private val viewModel by activityViewModels<MainVm>()

    private val mainVm by viewModels<MainVm>()

    private val loadList get() = if ("title0" == name) viewModel.loadSecondFragment1 else viewModel.loadList

    private val adapter by lazy {
        SecondAdapter(requireContext(), mainVm).also {
            it.setOnLoadMoreListener { loadData() }
        }
    }

    private val adapter2 by lazy {
        ConcatAdapter(adapter)
    }

    private val owner get() = viewLifecycleOwner

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        println("FragmentForSecond.onCreateView lwl name=${name} viewModel=${viewModel}")
        return inflater.inflate(R.layout.fragment_for_second, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvData = view.findViewById<RecyclerView>(R.id.rvData)


        loadList.observe {
            it.success {
                println("SecondFragment.onViewCreated lwl name=${name} observe adapter.dataSize=${adapter.dataSize} list=${this.list.size} ")
                if (adapter.dataSize == 0) {
                    adapter.refreshData(this.list, scrollToTop = false)
                } else {
                    adapter.addData(this.list)
                }
            }
        }

        if ("title0" == name) {
            viewModel.loadSecondFragment1(viewModel.fragmentPageIndex, refresh = false, force = false)
        } else {
            viewModel.loadList(viewModel.pageIndex, refresh = false, force = false)
        }

        rvData.layoutManager = LinearLayoutManager(requireContext())
        println("SecondFragment.onViewCreated lwl name=${name} 设置adapter")
        rvData.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        println("FragmentForSecond.onDestroyView lwl name=${name}")
    }


    private fun loadData() {
        println("SecondFragment.loadData lwl name=${name} pageIndex=${viewModel.pageIndex}")

        if ("title0" == name) {
            viewModel.loadSecondFragment1(viewModel.fragmentPageIndex++, refresh = false, force = true)
        } else {
            viewModel.loadList(viewModel.pageIndex++, refresh = false, force = true)
        }

    }

}

class SecondAdapter(context: Context, private val mainVm: MainVm) : SingleAdapterAbs<String>(context) {

//    constructor(context: Context, list: List<String>) : this(context) {
//        println("SecondAdapter. lwl list=${list.size}")
//        dataList.addAll(list)
//        showDataList.addAll(list)
//    }

    override fun ClaBaseViewHolder<String>.initHolder() {
        itemView.setOnClickListener {
            mainVm.loadList1().observeForever("second_adapter_item_click")
        }
    }

    override fun ClaBaseViewHolder<String>.bindHolder(t: String, pos: Int, payload: String?) {
        val tv = itemView.covert<TextView>()
        tv.text = t
    }

    override fun createItemView() = TextView(context).also { tv ->
        tv.id = R.id.last_toast_time
        tv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tv.setPadding(10.dp)
        tv.gravity = Gravity.CENTER
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    }
}


