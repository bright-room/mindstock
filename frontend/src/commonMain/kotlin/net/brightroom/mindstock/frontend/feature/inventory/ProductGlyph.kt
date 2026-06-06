package net.brightroom.mindstock.frontend.feature.inventory

import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName

/** 商品名から表示アイコンを推定(暫定。将来 domain がカテゴリを持てば差し替え)。 */
fun glyphForProductName(name: String): AppIconName =
    when {
        listOf("ソープ", "シャンプー", "ハンドクリーム", "洗剤").any { it in name } -> AppIconName.Drop
        listOf("ペーパー", "ティッシュ", "トイレット").any { it in name } -> AppIconName.Paper
        listOf("卵", "たまご").any { it in name } -> AppIconName.Egg
        listOf("牛乳", "ミルク", "ジュース", "茶", "水", "ボトル").any { it in name } -> AppIconName.Bottle
        listOf("醤油", "塩", "マヨネーズ", "調味").any { it in name } -> AppIconName.Salt
        listOf("電池", "バッテリ").any { it in name } -> AppIconName.Bolt
        else -> AppIconName.Box
    }
