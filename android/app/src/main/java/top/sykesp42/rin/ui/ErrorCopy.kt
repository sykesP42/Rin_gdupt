package top.sykesp42.rin.ui

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import top.sykesp42.rin.data.ApiError

object ErrorCopy {
    fun login(error: Throwable): String = when (error) {
        is ApiError -> when (error.code) {
            "INVALID_CREDENTIALS" -> "用户名或密码不正确"
            "ACCOUNT_LOCKED" -> "账号被锁，稍后再试或联系管理员"
            "ACCOUNT_DISABLED" -> "账号已停用，联系管理员"
            "RATE_LIMITED" -> "试得太频繁，稍等一下"
            "UNAUTHORIZED", "HTTP_401" -> "登录已失效，重新登录"
            "VALIDATION_ERROR" -> error.message.ifBlank { "检查输入后重试" }
            else -> error.message.ifBlank { "登录失败，检查服务地址和网络" }
        }
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            "连不上服务，检查服务地址和网络"
        is IOException -> "连不上服务，检查网络"
        is IllegalArgumentException -> "服务地址格式不正确"
        else -> "登录失败，稍后重试"
    }

    fun forCode(code: String, fallback: String = ""): String = when (code) {
        "UNAUTHORIZED", "HTTP_401" -> "登录已失效，重新登录"
        "RATE_LIMITED" -> "操作太频繁，稍后再试"
        "UPSTREAM_RESTRICTED" -> "教务系统没响应，稍后再试"
        "JWXT_ACCOUNT_REQUIRED" -> "先在网页端连接教务系统"
        "OFFLINE", "HTTP_ERROR" -> "网络不可用，稍后重试"
        "VALIDATION_ERROR" -> fallback.ifBlank { "提交的内容不符合要求" }
        else -> fallback.ifBlank { "请求失败，稍后重试" }
    }

    fun isAuth(code: String, status: Int = 0): Boolean =
        status == 401 || code == "UNAUTHORIZED" || code == "HTTP_401"

    fun from(error: Throwable): String = when (error) {
        is ApiError -> forCode(error.code, error.message)
        is IOException -> "网络不可用，稍后重试"
        else -> "请求失败，稍后重试"
    }
}
