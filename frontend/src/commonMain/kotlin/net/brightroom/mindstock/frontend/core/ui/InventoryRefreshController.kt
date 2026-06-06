package net.brightroom.mindstock.frontend.core.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 在庫に影響する mutation（補充/消費/訂正/setWanted）の単一シグナル。
 * mutation した VM が request()、各タブの一覧 VM を Compose 層で collect→reload する。
 */
class InventoryRefreshController {
    private val _signal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signal: SharedFlow<Unit> = _signal.asSharedFlow()

    fun request() {
        _signal.tryEmit(Unit)
    }
}
