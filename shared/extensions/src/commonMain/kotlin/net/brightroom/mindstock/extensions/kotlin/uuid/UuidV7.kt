package net.brightroom.mindstock.extensions.kotlin.uuid

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * UUID v7(時系列順に並ぶ128-bit identifier)を新規生成する。
 *
 * 集約ルートの ID 生成に使う。ミリ秒精度の time-based prefix を持つため、
 * 同じ集約ルートの ID は `ORDER BY id` で生成順に近い順序で並ぶ。
 */
@OptIn(ExperimentalUuidApi::class)
public fun newUuidV7(): Uuid = Uuid.generateV7()
