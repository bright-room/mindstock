## 概要

<!-- 何を・なぜ変更したか。背景や関連する設計判断があれば記載 -->

## 変更内容

<!-- 主な変更点を箇条書きで -->

-

## ラベル(リリースノート分類)

リリースノートは付与ラベルで自動分類される(`.github/release.yml`)。**少なくとも 1 つの `Kind:` ラベルを付ける**こと。

- [ ] `Kind: Feature` — 新機能
- [ ] `Kind: Enhancement` — 機能強化
- [ ] `Kind: Bug Fix` — バグ修正
- [ ] `Kind: Refactoring` — 内部リファクタリング(API 破壊なし)
- [ ] `Kind: Tests` — テスト追加・修正
- [ ] `Kind: Documentation` — ドキュメント
- [ ] `Kind: Dependencies` — 依存更新
- [ ] `Impact: Breaking` — 後方互換が失われる破壊的変更(該当時のみ)
- [ ] `Meta: Release note ignored` — リリースノートに載せない(雑多な内部変更)

## 確認

- [ ] `./gradlew test jvmTest` green
- [ ] 該当する検証(統合テスト / 実描画 eyeball 等)を実施した
