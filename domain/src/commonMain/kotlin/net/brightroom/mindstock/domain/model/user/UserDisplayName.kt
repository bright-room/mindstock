package net.brightroom.mindstock.domain.model.user

import kotlinx.datetime.Instant

/**
 * User の表示名変更履歴の 1 行。
 */
public class UserDisplayName(
    public val id: UserDisplayNameId,
    internal val userId: UserId,
    internal val displayName: DisplayName,
    internal val createdAt: Instant,
)
