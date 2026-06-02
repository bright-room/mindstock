package net.brightroom.mindstock.application.repository.invitation

import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode

interface InvitationRegisterRepository {
    /**
     * invitations(insert-once)+ 有効 event を INSERT。
     * code PK 衝突時は呼び出し側が code を再生成した Invitation で再試行する(最大 3 回)。
     * 衝突は unique violation を InvitationCodeCollisionException 等で表現せず、実装内でリトライ。
     */
    fun issue(invitation: Invitation): Invitation

    /** 無効 event を append(revoke)。 */
    fun revoke(code: InvitationCode)
}
