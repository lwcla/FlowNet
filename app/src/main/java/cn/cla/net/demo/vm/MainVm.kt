package cn.cla.net.demo.vm

import android.app.Application
import androidx.lifecycle.viewModelScope
import cn.cla.library.net.vm.BaseViewModel
import kotlinx.coroutines.launch

class MainVm(app: Application) : BaseViewModel(app) {

    private val repo by lazy { MainRepository() }

    private val _homeDataFlow = createLiveFlow<String>()
    val homeDataState = _homeDataFlow.switchMap() {
        repo.loadHomeBanner()
    }

    /** 首页banner */
    fun loadHomeBanner(force: Boolean) {
        viewModelScope.launch {
            _homeDataFlow.setValue("这是设置的值", force)
        }
    }
}