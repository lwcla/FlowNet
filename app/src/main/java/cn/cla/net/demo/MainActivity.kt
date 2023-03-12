package cn.cla.net.demo

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import cn.cla.library.net.entity.success
import cn.cla.net.demo.config.jumpAty
import cn.cla.net.demo.utils.findView
import cn.cla.net.demo.vm.MainVm
import com.google.gson.Gson


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

        tvRequest.setOnClickListener {
            tvJson.text = ""
//            mainVm.loadHomeBanner(force = true)
            mainVm.loadList(++index, refresh = false, force = true)
        }

        tvRefresh.setOnClickListener {
            tvJson.text = ""
            index = 0
            mainVm.loadList(index, refresh = true, force = true)
        }

        btnToSecond.setOnClickListener {
            SecondAty.launch(this)
        }

        btnToThird.setOnClickListener {
            jumpAty<ThirdAty>()
        }

        mainVm.loadList.observe(owner) {
            println("MainActivity.onCreate lwl loadList res=$it")
            it.success {
                tvJson.text = "${tvJson.text.toString()}\n${list.toString()}"
            }
        }

        mainVm.loadList(0, refresh = false, force = false)

//        mainVm.homeDataState.observe(owner) {
//            println("lwl MainActivity.onCreate homeDataState11 res=$it")
//            it.success {
//                tvJson.text = "${tvJson.text}\n\n${gson.toJson(this)}"
//            }
//        }
//
//        lifecycleScope.launch {
//            delay(1000)
//            mainVm.homeDataState.observe(owner, Lifecycle.State.RESUMED) {
//                println("lwl MainActivity.onCreate homeDataState22 res=$it")
//                it.success {
//                    tvJson.text = "${tvJson.text}\n\n${gson.toJson(this)}"
//                }
//            }
//
//            mainVm.loadHomeBanner(force = true)
//        }
//
//        mainVm.loadHomeBanner(force = false)
    }
}


