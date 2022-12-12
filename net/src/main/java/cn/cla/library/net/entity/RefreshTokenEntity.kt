package cn.cla.library.net.entity


/**
 * 刷新token
 *
 * @property code 后台返回的code
 * @property newToken 新的token
 * @property message 请求的错误信息
 */
data class RefreshTokenEntity(val code: Int, val newToken: String, val message: String?)