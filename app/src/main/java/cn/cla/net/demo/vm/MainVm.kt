package cn.cla.net.demo.vm

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import cn.cla.library.net.vm.BaseViewModel
import kotlinx.coroutines.launch

class MainVm(app: Application) : BaseViewModel(app) {

    private val repo by lazy { MainRepository() }

    private val _homeDataFlow = createLiveFlow<String>()
    val homeDataState = _homeDataFlow.switchMap() {
        repo.loadHomeBanner()
    }

    private val _liveData = MutableLiveData<Int>()
    val liveData = _liveData.switchMap { params ->
        val list = mutableListOf<String>()
        repeat(10) {
            list.add("${params}-$it")
        }

        liveData {
            emit(list)
        }
    }


    /**
     * 首页banner
     */
    fun loadHomeBanner(force: Boolean) {
        viewModelScope.launch {
            _homeDataFlow.setValue("这是设置的值", force)
        }
    }

    private var index = 0

    fun liveDataTest() {
        _liveData.postValue(index++)
    }

}