package net.brightroom.mindstock.frontend.designsystem.atom

import kotlin.math.max

/** 「余裕のある」基準量 = max(min*2, min+3, qty, 1)。core.jsx StockBar より。 */
fun comfortableStock(
    qty: Int,
    min: Int,
): Int = max(max(min * 2, min + 3), max(qty, 1))

fun fillFraction(
    qty: Int,
    min: Int,
): Float = (qty.toFloat() / comfortableStock(qty, min)).coerceIn(0f, 1f)

fun minFraction(
    qty: Int,
    min: Int,
): Float = (min.toFloat() / comfortableStock(qty, min)).coerceIn(0f, 1f)
