# 02. ICONIX(システム設計)

ICONIX: ドメインモデル(01 の A-3)→ ユースケース記述 → ロバストネス図(boundary/control/entity)→ シーケンス図 → 詳細クラス図。

**boundary/control/entity を本プロジェクトの層へ対応付ける(CLAUDE.md 原則 #1):**

| ICONIX | 本プロジェクトの層 / モジュール |
|---|---|
| **boundary** | フロント画面(`:frontend` Compose)+ RPC エンドポイント(`:rpc` の `@Rpc` interface / `:backend:api` presentation Controller) |
| **control** | application 層 Service(`:backend:core` の Service interface + orchestration) |
| **entity** | domain 集約/VO(`:domain`)+ infrastructure DataSource(`:backend:core`、Exposed) |

訂正は **append-only**(元 movement を残し訂正 movement を追記)、訂正は **member も可** とする(確定事項)。

---

## B-1. ユースケース記述(代表 5 件)

### UC11+13: JAN で商品を追加する(マスタ→外部API→手入力)
**主アクター:** member / owner
**事前条件:** 世帯がアクティブ。
**基本フロー:**
1. ユーザーが商品追加画面で JAN を入力 or バーコードをスキャン。
2. システムが大元マスタ(CatalogItem)を JAN 照合。**ヒット**→ 商品名・推奨単位を確定表示(名前は編集不可)。
3. マスタ未存在 → 外部 API(楽天/Yahoo)を JAN 照会。**ヒット**→ 取得名・単位を取得し DB(マスタ)へ保存、確定表示(名前は編集不可)。
4. どこにも無い → 「商品情報なし」。ユーザーが**商品名を手入力**(編集可)。
5. ユーザーが単位(推奨をデフォルト)・最低在庫を決めて確定。
6. システムが世帯に Product を採用(qty=0)。JAN を保持。
**代替/例外:**
- 2/3 で同一 JAN の Product が既に世帯にある → 「すでに世帯に登録されています」で**登録不可**。
- 外部 API がレートリミット/失敗 → 手入力フォールバック(4 へ)。

### UC14/15: 在庫を補充/消費する
1. ユーザーが商品で補充(or 消費)を選び、数量・発生日時・メモを入力。
2. システムが `qty ± n`(消費は `qty>=0` を保証)、StockMovement(replenish/consume)を**追記**。
3. 補充時は `wanted=false` に戻す。

### UC21: 記録を訂正する(append-only)
1. ユーザーが履歴の movement を選び、訂正後数量・理由を入力。
2. システムが元 movement を残したまま **訂正 movement(kind=correction, targetId, reason)を追記**。
3. Product.qty を movement 畳み込みで再計算。

### UC4: 招待リンク/コードで世帯に参加する
1. 招待リンク(`mindstock.app/join/CODE`)を開く → 参加ランディング(誰が・どの世帯に・付与権限)。
2. ログイン(未ログイン時)→ 表示名(初回のみ)→ 参加確認。
3. システムが InviteCode を検証(有効)し、付与 role で HouseholdMember を追加(1 コードで複数人が参加可)。
**例外:** コードが無効(取消/再発行で失効)→ エラー画面。

### UC8: 招待を発行する(owner)
1. owner が付与 role(編集/閲覧)を選び発行。
2. システムが 6 桁コード・リンクを生成(**有効期限なし・1 コードで複数参加可**)。世帯の有効招待は 0..1(再発行で旧コードを無効化)。

---

## B-2. ロバストネス図

### UC11+13(JAN 追加)

```mermaid
graph LR
    U((ユーザー))
    B1[商品追加画面]:::b
    B2[BarcodeScanner]:::b
    C1{{ProductAdoptService}}:::c
    C2{{CatalogLookupService}}:::c
    E1[(CatalogItem)]:::e
    E2[(Product)]:::e
    EXT["楽天 / Yahoo API"]:::b

    U --> B1
    U --> B2
    B1 --> C2
    B2 --> C2
    C2 --> E1
    C2 -->|未存在時| EXT
    EXT --> C2
    C2 -->|取得結果を保存| E1
    B1 --> C1
    C1 -->|JAN重複検査| E2
    C1 -->|採用/復元| E2

    classDef b fill:#e8f0fe,stroke:#4285f4;
    classDef c fill:#fef7e0,stroke:#f9ab00;
    classDef e fill:#e6f4ea,stroke:#34a853;
```

### UC21(訂正・append-only)

```mermaid
graph LR
    U((ユーザー))
    B[商品詳細/訂正シート]:::b
    C{{StockCorrectionService}}:::c
    E1[(Product)]:::e
    E2[(StockMovement)]:::e

    U --> B
    B -->|訂正後数量+理由| C
    C -->|訂正 movement を追記| E2
    C -->|qty 再計算| E1

    classDef b fill:#e8f0fe,stroke:#4285f4;
    classDef c fill:#fef7e0,stroke:#f9ab00;
    classDef e fill:#e6f4ea,stroke:#34a853;
```

---

## B-3. シーケンス図

### UC11+13: JAN 追加(フロント → RPC → application → infra/domain → 外部)

```mermaid
sequenceDiagram
    actor U as ユーザー
    participant FE as AddProduct(Compose)
    participant RPC as CatalogRpcService(@Rpc)
    participant CTL as CatalogController
    participant SVC as CatalogLookupService
    participant REPO as CatalogItemRepository
    participant API as ExternalProductGateway
    participant PRPC as ProductRpcService
    participant PSVC as ProductAdoptService
    participant PREPO as ProductRepository

    U->>FE: JAN 入力/スキャン
    FE->>RPC: lookupByJan(jan)
    RPC->>CTL: 認可(member+)
    CTL->>SVC: lookup(jan)
    SVC->>REPO: findByJan(jan)
    alt マスタにヒット
        REPO-->>SVC: CatalogItem
    else 未存在
        SVC->>API: fetch(jan)
        API-->>SVC: ExternalProductInfo(or なし)
        SVC->>REPO: save(CatalogItem) [キャッシュ]
    end
    SVC-->>FE: LookupResult(name, unit, source, editable?)
    U->>FE: 単位/最低在庫を確定
    FE->>PRPC: adopt(jan or name, unit, min)
    PRPC->>PSVC: adopt(...)
    PSVC->>PREPO: existsByJan(householdId, jan)
    alt 重複
        PSVC-->>FE: RpcError(AlreadyRegistered)
    else 採用/アーカイブ復元
        PSVC->>PREPO: save(Product)
        PSVC-->>FE: RpcResult.Success
    end
```

### UC21: 訂正(append-only)

```mermaid
sequenceDiagram
    actor U as ユーザー
    participant FE as CorrectionSheet
    participant RPC as StockRpcService(@Rpc)
    participant CTL as StockController
    participant SVC as StockCorrectionService
    participant REPO as StockRepository

    U->>FE: 訂正後数量 + 理由
    FE->>RPC: correct(productId, movementId, qty, reason)
    RPC->>CTL: 認可(member+)
    CTL->>SVC: correct(...)
    SVC->>REPO: load(Stock 集約 = product + movements)
    SVC->>SVC: stock.correct(movementId, qty, reason) [訂正 movement 追記 + qty 再計算]
    SVC->>REPO: append(correctionMovement) + update(qty)
    SVC-->>FE: RpcResult.Success
```

---

## B-4. 詳細クラス図(domain + 公開 API)

```mermaid
classDiagram
    class Household {
        +rename(name, by)
        +join(resident, grantedRole)
        +changeRole(target, role, by)
        +removeMember(target, by)
        +leave(by)
    }
    class Invitation {
        +code: InvitationCode
        +grantedRole: 世帯での役割
        +有効性: 招待コード有効性
        +usable() Boolean
    }
    class Stock {
        +replenish(qty, at, actor, note) Stock
        +consume(qty, at, actor, note) Stock
        +correct(target, qty, reason, actor, at) Stock
        +archive() Stock
        +unarchive() Stock
        +want() Stock
        +unwant() Stock
        +currentQuantity() Int
        +status() 在庫状態
        +onShoppingList() Boolean
    }
    class Product {
        +catalogItem: CatalogItem
        +setting: StockingPolicy
        +image: ProductImage
        +status: 商品状態
    }
    class StockMovement {
        <<sealed>>
        +actor: Resident
    }
    class CatalogItem {
        +content: CatalogContent
        +barcode: Barcode
        +origin: 仕入元
    }
    StockMovement <|-- Replenishment
    StockMovement <|-- Consumption
    StockMovement <|-- Correction
    Household *-- Profile
    Household *-- HouseholdMember
    Invitation ..> Household : householdId 参照
    HouseholdMember --> Resident
    Stock --> Product
    Product --> CatalogItem
    Stock *-- StockMovement
```

ビジネスロジックは domain に置き(リッチドメイン)、Service は薄い orchestration。在庫操作(`currentQuantity()`/`status()`/`onShoppingList()`/補充・消費・訂正・アーカイブ)は **`Stock` 集約**のメソッド、世帯固有設定(単位/最低在庫/画像)は `Product`。VO 違反は IAE、前提崩れは専用例外で表す(`DomainException` の sealed 階層は作らない)。

---

## B-5. モジュール / レイヤマッピング

```
:domain     resident{Resident(id+profile), profile(Profile/DisplayName), identity(ResidentId), identity/auth(AuthIdentity=境界VO)}・household・catalog・inventory(Stock/Product/StockMovement/ShoppingList) 各コンテキスト + VO + 専用例外
:rpc        @Rpc service interface, RpcResult, RpcError, DTO
:shared     既存維持(KrpcJson, datetime/serialization 拡張)
:backend:core
  application/   Service interface + Repository interface(control)
  infrastructure/ Exposed DataSource, ExternalProductGateway(楽天/Yahoo)(entity I/O)
:backend:api    Ktor 起動 + presentation/rpc Controller(@Rpc 実装) + auth(Zitadel)
:backend:schedules バッチ(将来・通知等。招待は無期限のため期限掃除は不要)
:frontend   Compose 画面 + per-screen 状態 + RpcClient
```

### RPC サービス分割(`:rpc`、すべて `@Rpc` 必須)

| サービス | 主メソッド | 対応 UC |
|---|---|---|
| `ResidentRpcService` | `registerDisplayName`, `me` | 2 |
| `HouseholdRpcService` | `create`, `rename`, `leave`, `list`, `changeRole`, `removeMember`, `createInvite`, `revokeInvite`, `join`, `previewInvite` | 3,5–9 |
| `CatalogRpcService` | `search`, `lookupByJan` | 11,12 |
| `ProductRpcService` | `adopt`, `addCustom`, `list`, `listArchived`, `changeUnit/Image/Minimum`, `archive`, `unarchive`, `setWanted` | 10,13,16,19,20,22,23 |
| `StockRpcService` | `replenish`, `consume`, `correct`, `history`, `activity` | 14,15,17,21,24 |

戻り値は `RpcResult<T>`(エラーは `RpcError`)で表現し、**nullable 戻り値は使わない**。「不在」は例外/`RpcError` で表す。

### 認可

`@Rpc` Controller 層で role を検査(viewer=読取専用 / member=日々の在庫操作・訂正・採用 / owner=世帯/メンバー/マスタ編集)。Zitadel OIDC の JWT を WS ハンドシェイクで検証(既存 `:backend:api/configuration/auth` の方式を踏襲)。

---

## B-6. 将来対応予定(モデル化のみ・実装後回し)

- **消費予測「あと約 N 日」**: StockMovement(consume)から消費ペースを導出。現状 UI は表示のみ想定。
- **在庫減少のお知らせ(Web Push)**: `:backend:schedules` で閾値割れを検知し通知。UI トグルは OFF 固定・操作不可。
- **オフライン閲覧**: PWA キャッシュ。UI トグルは OFF 固定・操作不可。
