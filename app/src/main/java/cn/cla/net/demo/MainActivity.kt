package cn.cla.net.demo

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import cn.cla.library.net.entity.success
import cn.cla.net.demo.utils.findView
import cn.cla.net.demo.vm.MainVm
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private val mainVm by viewModels<MainVm>()

    private val tvRequest by findView<TextView>(R.id.tvRequest)
    private val tvJson by findView<TextView>(R.id.tvJson)

    private val gson by lazy { Gson() }

    private val owner get() = this


    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        tvRequest.setOnClickListener {
            tvJson.text = ""
            mainVm.loadHomeBanner(force = true)
        }

        mainVm.homeDataState.observe(owner) {
            println("lwl MainActivity.onCreate homeDataState11 res=$it")
            it.success {
                tvJson.text = "${tvJson.text}\n\n${gson.toJson(this)}"
            }
        }

        lifecycleScope.launch {
            delay(1000)
            mainVm.homeDataState.observe(owner, Lifecycle.State.RESUMED) {
                println("lwl MainActivity.onCreate homeDataState22 res=$it")
                it.success {
                    tvJson.text = "${tvJson.text}\n\n${gson.toJson(this)}"
                }
            }

            mainVm.loadHomeBanner(force = true)
        }

        mainVm.loadHomeBanner(force = false)
    }
}


