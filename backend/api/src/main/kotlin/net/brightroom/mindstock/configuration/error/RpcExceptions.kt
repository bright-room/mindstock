package net.brightroom.mindstock.configuration.error

/** 認証失敗 / Principal なし / User 解決失敗。 */
class UnauthorizedException(
    message: String = "Unauthorized",
) : RuntimeException(message)

/** 集約 resolve 失敗(例: Repository.findById() が null)。 */
class NotFoundException(
    message: String,
) : RuntimeException(message)
