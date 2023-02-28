package cn.cla.net.demo

import android.os.Bundle
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import cn.cla.library.net.entity.success
import cn.cla.net.demo.utils.findView
import cn.cla.net.demo.vm.MainVm
import com.google.gson.Gson


class MainActivity : AppCompatActivity() {

    private val mainVm by viewModels<MainVm>()

    private val tvRequest by findView<TextView>(R.id.tvRequest)
    private val tvJson by findView<TextView>(R.id.tvJson)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvRequest.setOnClickListener {
            tvJson.text = ""
            mainVm.loadHomeBanner().observeForever {
                println("lwl MainActivity.onCreate res=$it")
                it.success {
                    tvJson.text = Gson().toJson(this)
                }
            }
        }
    }
}


