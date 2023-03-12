package cn.cla.net.demo

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.cla.library.net.entity.success
import cn.cla.net.demo.utils.findView
import cn.cla.net.demo.vm.MainVm

class ThirdAty : AppCompatActivity() {

    private val mainVm by viewModels<MainVm>()

    private val rvData by findView<RecyclerView>(R.id.rvData)

    private val adapter by lazy {
        SecondAdapter(this).also {
            it.setOnLoadMoreListener {
                mainVm.loadList(mainVm.pageIndex++, refresh = false, force = true)
            }


        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_third)


        mainVm.loadList.observe(this) {
            it.success {
                println("ThirdAty.onCreate lwl observe dataSize=${adapter.dataSize} list=${list.size}")
                if (adapter.dataSize == 0) {
                    adapter.refreshData(list, scrollToTop = false)
                } else {
                    adapter.addData(list)
                }
            }
        }


        rvData.layoutManager = LinearLayoutManager(this)
        rvData.addItemDecoration(DividerItemDecoration(this, RecyclerView.VERTICAL))
        println("ThirdAty.onCreate lwl 设置adapter mainVm=${mainVm}")
        rvData.adapter = adapter

        mainVm.loadList(mainVm.pageIndex++, refresh = false, force = false)
    }
}