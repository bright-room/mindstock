package net.brightroom.mindstock.domain.model.user

import kotlinx.datetime.Instant

/**
 * アプリ内ユーザー集約。`zitadelSub` で外部認証(Zitadel)と紐づく。
 */
class User(
    val id: UserId,
    internal val zitadelSub: ZitadelSub,
    internal val createdAt: Instant,
)
