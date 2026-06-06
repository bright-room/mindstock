package net.brightroom.mindstock.frontend.core.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 再認証要求の単一シグナル。feature が request、app 受け口が collect して token 破棄→authorize。 */
class ReauthController {
    private val _signal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signal: SharedFlow<Unit> = _signal.asSharedFlow()

    fun request() {
        _signal.tryEmit(Unit)
    }
}
