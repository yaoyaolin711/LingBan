package com.agent.chat.domain.error

/**
 * 统一业务错误类型。原始异常不得直接传到 UI。
 */
sealed class AppError {
    data object NetworkUnavailable : AppError()
    data object Timeout : AppError()
    data object InvalidApiKey : AppError()
    data object RateLimitExceeded : AppError()
    data class ServerError(val code: Int) : AppError()
    data object ContextTooLong : AppError()
    data object Unknown : AppError()
}

fun AppError.userMessage(): String = when (this) {
    AppError.NetworkUnavailable -> "网络连接不可用，请检查网络后重试"
    AppError.Timeout -> "请求超时了，可能是网络较慢，点击重试"
    AppError.InvalidApiKey -> "模型配置有误，请前往设置检查 API Key 是否正确"
    AppError.RateLimitExceeded -> "请求太频繁了，稍等一下再试"
    is AppError.ServerError -> "服务暂时不可用，请稍后再试"
    AppError.ContextTooLong -> "这次对话内容有点多，试试新建一个会话"
    AppError.Unknown -> "出了点小问题，请重试"
}
