package net.brightroom.mindstock.domain.exception

/** 世帯メンバーでない resident が世帯リソースへアクセスした。認可失敗(横方向認可)。 */
class MembershipRequiredException(
    reason: String,
) : RuntimeException(reason)
