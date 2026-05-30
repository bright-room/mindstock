# mindstock ドメイン & 主要フロー整理

> 作成日: 2026-05-30
> 目的: 「ユーザーと世帯の作成 / ログイン / ログアウト / 在庫管理」の4フローと、その背後のドメイン構造を人間が一望できるよう整理する。頭の整理用。
> 手法: **sudoモデリング**(ドメインモデル図 + ユースケース)を軸に、**状態遷移図**(RDRA 簡略版)と **シーケンス図**(ICONIX 的)を組み合わせ。

---

## 0. 登場人物(システムコンテキスト)

```mermaid
flowchart LR
    user([ユーザー（人）])
    browser[ブラウザ<br/>Compose/Wasm frontend]
    backend[backend<br/>Ktor]
    zitadel[(Zitadel<br/>OIDC プロバイダ)]
    db[(PostgreSQL)]

    user -->|操作| browser
    browser <-->|① ログイン: Authorization Code + PKCE| zitadel
    browser <-->|② API: WebSocket + JWT| backend
    backend -->|③ 公開鍵で JWT 検証| zitadel
    backend <-->|④ 永続化| db
```

- **ログインする場所(Zitadel)** と **API を使う場所(backend)** は別。backend は発券にノータッチで、Zitadel の公開鍵で入場券(JWT)を検証するだけ。
- ブラウザ ↔ backend は WebSocket 一本。JWT は接続を開く瞬間に `Sec-WebSocket-Protocol` で同送する(ブラウザがその経路でしかヘッダを付けられないため)。

---

## 1. ドメインモデル(sudoモデリング: ドメインモデル図)

```mermaid
classDiagram
    class User {
        id
        zitadelSub（Zitadelアカウント識別子）
    }
    class Profile {
        displayName（表示名）
    }
    class Household {
        id
    }
    class HouseholdName {
        value（世帯名・後から変更可）
    }
    class HouseholdMembership {
        role: OWNER | MEMBER
        revoked（脱退したか）
    }
    class CatalogItem {
        name（商品名）
        unit（単位）
        ※全世帯で共有
    }
    class Product {
        minimumStock（最低在庫）
        archived
        ※世帯固有
    }
    class Stock {
        currentQuantity()（現在数量）
        needsReplenishment()（買い物要否）
    }
    class StockMovement {
        種別: 補充 | 消費
        quantity, occurredAt, note
    }

    User "1" -- "1" Profile : 表示名を持つ
    Household "1" -- "1" HouseholdName : 世帯名（最新値）
    User "1" -- "0..*" HouseholdMembership : 所属
    Household "1" -- "1..*" HouseholdMembership : メンバー
    Household "1" -- "0..*" Product : 採用した商品
    CatalogItem "1" -- "0..*" Product : 元になる商品概念
    Product "1" -- "1" Stock : 在庫状態
    Stock "1" -- "0..*" StockMovement : 補充/消費の履歴
```

### 読みどころ

- **User と Household は多対多**(`HouseholdMembership` で繋ぐ)。役割(OWNER/MEMBER)と脱退(revoked)を持つ。
  - MVP は **1 User = 1 世帯**に倒すが、データ構造は将来の「1 世帯 = 複数ユーザー」をそのまま許す形。
- **Household は名前(`HouseholdName`)を持つ**(`household_names` 事実テーブルで履歴、後から変更可)。ただし初回は表示名から導出したデフォルト名で自動作成するので、作成時にユーザーへは尋ねない。
- **CatalogItem は全世帯共有**、**Product は世帯固有**(CatalogItem を「採用」したもの + 最低在庫)。
- **在庫数は持たず、Stock が Movement(補充/消費)から計算する**(append-only)。

---

## 2. 状態遷移(RDRA 簡略版: ユーザーから見たアプリの状態)

「ログイン / 作成 / ログアウト」は、この1枚で繋がっている。

```mermaid
stateDiagram-v2
    [*] --> 未ログイン

    未ログイン --> 認証中 : ログイン押下（Zitadelへ）
    認証中 --> 判定中 : Zitadelから戻る（トークン取得）

    判定中 --> 在庫管理可能 : 登録済み（User+世帯あり）
    判定中 --> 表示名入力 : 未登録（JWTは有効だがUser無し）
    判定中 --> エラー : トークン無効/通信失敗

    表示名入力 --> 在庫管理可能 : 表示名入力 → User+世帯(デフォルト名)+OWNER を自動作成

    在庫管理可能 --> 未ログイン : ログアウト
    エラー --> 未ログイン : やり直し

    在庫管理可能 --> 在庫管理可能 : 補充 / 消費 / 一覧 / 買い物リスト
```

- **「表示名入力 → 在庫管理可能」の矢印が "ユーザーと世帯の作成"**(＝あなたの言う「アカウント作成フロー」)。User と世帯を**同時に**作る。
- **「未登録」状態は一瞬しか存在しない**(表示名を入れたら即・世帯持ちになる)。「登録済みだが世帯なし」という宙ぶらりんは設計上発生しない。

---

## 3. 各フローのシーケンス

### 3-A. ユーザーと世帯の作成(＝オンボーディング / 初回サインアップ)

```mermaid
sequenceDiagram
    actor U as ユーザー
    participant B as ブラウザ
    participant Z as Zitadel
    participant S as backend

    U->>B: ログイン押下
    B->>Z: Authorization Code + PKCE
    Z-->>B: 認可コード
    B->>Z: トークン交換
    Z-->>B: JWT（access / refresh / id）

    B->>S: 接続 + 登録状態を問い合わせ
    S-->>B: 「未登録」

    B->>U: 表示名の入力を求める
    U->>B: 表示名を入力
    B->>S: 登録（表示名）
    note over S: 1トランザクションで原子的に作成<br/>User + Household（デフォルト名） + OWNER membership
    S-->>B: 登録完了
    B->>U: 在庫一覧へ
```

> ポイント: 「登録」の一撃で **User と世帯と OWNER 資格** が揃う。世帯に入力項目が無いので、世帯作成は専用画面ではなく登録に同梱される。

### 3-B. ログイン(2回目以降 / 既存ユーザー)

```mermaid
sequenceDiagram
    actor U as ユーザー
    participant B as ブラウザ
    participant Z as Zitadel
    participant S as backend

    U->>B: アプリを開く（or ログイン押下）
    alt トークンが手元にあり有効
        B->>S: 接続 + 登録状態を問い合わせ
        S-->>B: 「登録済み」（表示名 など）
        B->>U: 在庫一覧へ
    else トークン無し/期限切れ
        B->>Z: Authorization Code + PKCE（or refresh）
        Z-->>B: JWT
        B->>S: 接続 + 登録状態を問い合わせ
        S-->>B: 「登録済み」
        B->>U: 在庫一覧へ
    end
```

> ポイント: ログインは「トークンを得て、登録状態を問い合わせ、登録済みなら在庫へ」。未登録だった場合は 3-A(表示名入力)へ分岐する。

### 3-C. ログアウト

```mermaid
sequenceDiagram
    actor U as ユーザー
    participant B as ブラウザ
    participant Z as Zitadel

    U->>B: ログアウト押下
    B->>B: 手元のトークンを破棄 / 接続を閉じる
    B->>Z: セッション終了（end_session）
    Z-->>B: 未ログイン画面へ戻す
```

> ポイント: backend 側に状態は持たない(ステートレス)。トークンを捨てて Zitadel のセッションを終わらせるだけ。

### 3-D. 在庫管理(その世帯の在庫を使う)

```mermaid
sequenceDiagram
    actor U as ユーザー
    participant B as ブラウザ
    participant S as backend

    note over B: ログイン済み = 自分の世帯ID が手元にある

    U->>B: 在庫一覧を開く
    B->>S: 世帯の在庫を取得（世帯ID）
    S-->>B: 在庫一覧（商品ごとの現在数量・買い物要否）
    B->>U: 一覧表示（不足は強調）

    alt 補充した
        U->>B: 補充（商品・数量）
        B->>S: 補充を記録
        S-->>B: OK
    else 消費した
        U->>B: 消費（商品・数量）
        B->>S: 消費を記録
        S-->>B: OK
    end

    B->>U: 数量を再計算して表示更新
    note over B: 買い物リストは在庫一覧から<br/>クライアント側で導出（追加通信なし）
```

> ポイント: 在庫操作はすべて「自分の世帯ID」を前提に動く。世帯ID はログイン直後に確定済み(3-A / 3-B)。買い物リストは在庫から計算で出すので別取得は不要。

---

## 4. 4フローの関係まとめ

```mermaid
flowchart TD
    login[ログイン<br/>3-B] --> check{登録済み?}
    check -->|はい| inventory[在庫管理<br/>3-D]
    check -->|いいえ| onboard[ユーザーと世帯の作成<br/>3-A]
    onboard --> inventory
    inventory --> logout[ログアウト<br/>3-C]
    logout --> login
```

- **入口は常にログイン**。その結果で「在庫管理(既存)」か「作成(初回)」に分岐。
- **作成は初回だけ**通る特別な分岐。2回目以降は素通りして在庫管理へ。
- ログアウトすると入口に戻る。

---

## 5. 確定した前提(2026-05-30 合意)

1. **ユーザーと世帯の作成 = 3-A**: 登録の一撃で User + 世帯(デフォルト名)+ OWNER を**原子的に**同時作成。
2. **中間状態を作らない**: 登録 = 即・世帯持ち。「未登録だが世帯なし」は発生しない(staged commit 不要)。
3. **1 User = 1 世帯(MVP)** / データ構造は将来の「1 世帯 = 複数ユーザー」を許す(membership に `UNIQUE(user_id)` を張らない)。
4. **在庫管理は常に「自分の世帯」前提**(世帯切替は将来)。
5. **世帯は名前を持つ**(後から変更可)。**登録判定**は専用フラグを持たず、user 行 + OWNER membership の存在から導出。
6. **`CatalogItem.unit` → `Product` 移動**は妥当だが MVP 価値が薄いため別 Plan に切り出し(本図では現状の CatalogItem に unit がある前提のまま)。
