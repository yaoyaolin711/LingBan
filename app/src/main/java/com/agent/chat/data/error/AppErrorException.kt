package com.agent.chat.data.error

import com.agent.chat.domain.error.AppError

/** 携带已映射 AppError 的失败包装，便于 Repository → ViewModel 传递。 */
class AppErrorException(
    val appError: AppError,
    cause: Throwable? = null,
) : Exception(appError.toString(), cause)
