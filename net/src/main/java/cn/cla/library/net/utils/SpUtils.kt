package cn.cla.library.net.utils

import android.content.Context
import androidx.core.content.edit

internal const val SP_NAME = "cla_net_library"

internal const val TOKEN_KEY = "token_key"

internal var Context.token: String
    get() = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).getString(TOKEN_KEY, "") ?: ""
    set(value) {
        getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit { this.putString(TOKEN_KEY, value) }
    }