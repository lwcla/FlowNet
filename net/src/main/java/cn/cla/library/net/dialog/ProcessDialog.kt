package cn.cla.library.net.dialog

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.DialogFragment
import cn.cla.library.net.R
import cn.cla.library.net.utils.setBarColor

/**
 * 表示进度用的弹窗
 */
internal class ProcessDialog() : DialogFragment() {

    constructor(isCancelAble: Boolean, text: String = "正在加载") : this() {
        cancelAble = isCancelAble
        tip = text
    }

    /**
     * 是否可以点旁边取消
     */
    var cancelAble = true
        set(value) {
            isCancelable = value
            field = value
        }

    private var tip: String = "正在加载"

    private val ctx get() = requireContext()
    private val aty get() = requireActivity()
    private val owner get() = viewLifecycleOwner

    //背景透明
    override fun getTheme(): Int = R.style.cla_net_custom_bottom_sheet_style

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        return inflater.inflate(R.layout.cla_net_dialog_process, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val progressBar = view.findViewById<ContentLoadingProgressBar>(R.id.progressBar)
        val tvTip = view.findViewById<TextView>(R.id.tvTip)

        tvTip.text = tip

        progressBar?.setBarColor(ctx, R.color.cla_net_c11)
    }

    override fun dismissAllowingStateLoss() {
        if (fragmentManager == null || !isAdded) {
            return
        }

        super.dismissAllowingStateLoss()
    }

    override fun dismiss() {
        if (fragmentManager == null || !isAdded) {
            return
        }

        super.dismiss()
    }
}