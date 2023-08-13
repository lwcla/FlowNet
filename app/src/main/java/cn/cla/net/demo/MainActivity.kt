package cn.cla.net.demo

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.doOnAttach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import cn.cla.library.net.config.ForeverActionEvent
import cn.cla.library.net.vm.observe
import cn.cla.net.demo.config.jumpAty
import cn.cla.net.demo.utils.findView
import cn.cla.net.demo.vm.MainVm
import com.google.gson.Gson
import com.jeremyliao.liveeventbus.LiveEventBus

class MainActivity : AppCompatActivity() {

    private val mainVm by viewModels<MainVm>()

    private val tvRequest by findView<TextView>(R.id.tvRequest)
    private val tvRefresh by findView<TextView>(R.id.tvRefresh)
    private val tvJson by findView<TextView>(R.id.tvJson)
    private val btnToSecond by findView<Button>(R.id.btnToSecond)
    private val btnToThird by findView<Button>(R.id.btnToThird)

    private val gson by lazy { Gson() }

    private val owner get() = this


    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var index = 0

        println("MainActivity.onCreate lwl aty=$this")


        LiveEventBus.get<ForeverActionEvent>("second_adapter_item_click").observe(this) {
            println("MainActivity.onCreate lwl ForeverActionEvent value=$it")
        }

        mainVm.loadList.observe(Lifecycle.State.RESUMED) {
            println("MainActivity.onCreate lwl loadList observe1 value=$it")
        }

        tvRequest.setOnClickListener {
            println("MainActivity.onCreate lwl click request")
            tvJson.text = ""
//            mainVm.loadHomeBanner(force = true)
//            mainVm.loadList(++index, refresh = false, force = true)

            mainVm.loadList1().observe {
                println("MainActivity.onCreate lwl loadList1 observe2 value=$it")
            }

//            lifecycleScope.launch {
//                val resource = mainVm.loadList1().await()
////                val resource = mainVm.loadList1().await(Lifecycle.State.RESUMED)
//                println("MainActivity.onCreate lwl loadList1 await value=$resource")
//            }
        }

        tvRefresh.setOnClickListener {
//            tvJson.text = ""
//            index = 0
//            mainVm.loadList(index, refresh = true, force = true)
            recreate()
        }

        btnToSecond.setOnClickListener {
            SecondAty.launch(this)
        }

        btnToThird.setOnClickListener {
            jumpAty<ThirdAty>()
        }
    }
}

class ClaView(context: Context, attributeSet: AttributeSet? = null) : AppCompatTextView(context, attributeSet) {

    private val mainVm by lazy {
        findViewTreeViewModelStoreOwner()?.let { owner ->
            val viewModelStore = owner.viewModelStore
            viewModelStore.let { ViewModelProvider(owner)[MainVm::class.java] }
        }
    }

    init {
        doOnAttach {
//            mainVm?.loadList1()?.observe { resource ->
//                println("ClaView.init resource=$resource")
//            }

//            findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
//                val resource = mainVm?.loadList1()?.await()
//                println("ClaView.init resource=$resource")
//            }
        }
    }

}


