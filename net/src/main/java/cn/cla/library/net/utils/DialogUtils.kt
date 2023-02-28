package cn.cla.library.net.utils

import android.content.Context
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

/**
 * 显示DialogFragment
 */
internal inline fun <reified T : DialogFragment> Context?.showDialogFragment(show: (FragmentManager, String) -> T): T? {

    if (this == null) {
        return null
    }

    return (this as? FragmentActivity?).showDialogFragment<T>(show)
}

/**
 * 显示DialogFragment
 */
internal inline fun <reified T : DialogFragment> FragmentActivity?.showDialogFragment(show: (FragmentManager, String) -> T): T? {

    if (this == null) {
        return null
    }

    val tag = T::class.java.simpleName
    val dialog = supportFragmentManager.findFragmentByTag(tag) as? T?
    if (dialog != null) {
        val bt = supportFragmentManager.beginTransaction()
        bt.remove(dialog)
        bt.commitNowAllowingStateLoss()
    }

    return if (!isFinishing && dialog?.isAdded != true) {
        //关闭输入法
        closeInput()
        show(supportFragmentManager, tag)
    } else {
        null
    }
}

internal inline fun <reified T : DialogFragment> Context?.showDialogSimple(createDialog: () -> T): T? {
    return showDialogFragment { manager, tag ->
        val dialog = createDialog()
        dialog.show(manager, tag)
        dialog
    }
}

internal inline fun <reified T : DialogFragment> Fragment.showDialogSimple(createDialog: () -> T) = requireContext().showDialogSimple(createDialog)