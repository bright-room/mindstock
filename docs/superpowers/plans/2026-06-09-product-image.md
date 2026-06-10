# 商品画像(アップロード/Garage 保存/presigned 表示)Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 商品に画像をアップロードし、一覧/詳細/設定で表示できる E2E 経路を、Garage(S3 互換)保存 + presigned URL 直 fetch + Wasm ピッカーで実装する。

**Architecture:** アップロードは backend 経由(base64 を WS-RPC→検証/512px 縮小/JPEG/sha256→Garage put)。表示は backend が presigned GET URL を発行し、frontend(Ktor HttpClient)が Garage から生バイトを直 fetch して `decodeToImageBitmap()` で描画。保存キーは sha256 content-addressed。`product_revisions.image_ref`(既存 varchar512)に ref を入れるだけで **DB スキーマ変更なし**。

**Tech Stack:** Kotlin/JVM(Ktor, Exposed, aws-sdk-kotlin S3), kotlinx-rpc(WS), Compose Multiplatform(Kotlin/Wasm, Ktor client, `decodeToImageBitmap`), Garage(`dxflrs/garage`), javax.imageio。

**Spec:** `docs/superpowers/specs/2026-06-09-product-image-design.md`

---

## 設計上の確定事項(実装前提)

- **保存先 = Garage(S3 互換)**。MinIO ではない。DB bytea は不採用。
- **アップロード = backend 経由・単純同期**(楽観 UI / クライアント縮小なし。ユーザ判断 2026-06-09)。
- **表示 = presigned GET URL 直 fetch**(有効期限 **1 時間**・期限切れは再取得)。
- **保存キー = sha256(処理後 JPEG バイト)の hex**(content-addressed・dedup・immutable)。`image_ref varchar(512)` にそのまま入る → **DB スキーマ変更なし**。
- **画像処理 = javax.imageio**(JDK 内蔵・新規依存なし): decode → 最大辺 512px に縮小 → JPEG 品質 0.85 再エンコード。入力上限 8MB・decode 不能は拒否。
- **storage repo / 新 service メソッドは `suspend`**: aws-sdk-kotlin S3Client は suspend。Ktor coroutine 上で `runBlocking` を避け、既に suspend な Controller までそのまま伝播させる(JDBC blocking の既存 service とは非対称だが、async network I/O のため妥当)。
- **`StoredImage` / `ImageMediaType` は作らない**(presigned 配信で backend はバイトを読まないため)。
- 新規ドメイン VO は `RawImageUpload`(ByteArray)と `ImageUrl`(String)の 2 つのみ。

---

## File Structure

**新規作成**

| パス | 責務 |
|---|---|
| `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/RawImageUpload.kt` | クライアント原バイトの VO |
| `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/ImageUrl.kt` | presigned URL の VO |
| `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product/ProductImageStorageRepository.kt` | storage repo interface(suspend store/presignedUrl) |
| `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/storage/image/ImageProcessor.kt` | 純関数: 原バイト→(処理後 JPEG, sha256 ref)。Garage 不要 |
| `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/storage/image/ProductImageStorageDataSource.kt` | S3Client で Garage put / presign |
| `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/external/storage/StorageProperties.kt` | `external.storage.*` config |
| `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/external/storage/StorageConfiguration.kt` | S3Client 生成 + DI provide |
| `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/UploadImageRequest.kt` | アップロード wire 型(base64) |
| `docker/garage.toml` | Garage 設定 |
| `docker/garage-init.sh` | bucket/key/CORS 初期化 → `.env.garage` |
| `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/image/ProductImageLoader.kt` | imageUrl RPC + HttpClient fetch + productId→ImageBitmap キャッシュ |
| `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/ImageField.kt` | 画像欄 composable |
| `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/core/image/ImagePicker.kt` | Wasm `<input type=file>` ピッカー(expect/actual) |

**変更**

| パス | 変更 |
|---|---|
| `gradle/libs.versions.toml` | aws-sdk-kotlin s3 依存追加 |
| `backend/core/build.gradle.kts` | `implementation(libs.aws.sdk.kotlin.s3)` |
| `backend/api/build.gradle.kts` | integrationTest に `TEST_STORAGE_*` env 受け渡し |
| `compose.yml` | `garage` + `garage-init` サービス追加 |
| `backend/api/src/main/resources/application.yaml` | `external.storage.*` + modules に StorageConfiguration 追加 |
| `backend/api/.../configuration/di/DependenciesConfiguration.kt` | `provide<ProductImageStorageRepository>` |
| `rpc/.../product/ProductRegisterRpcService.kt` | `uploadImage` 追加 |
| `rpc/.../product/ProductRpcService.kt` | `imageUrl` 追加 |
| `backend/core/.../application/service/product/ProductRegisterService.kt` | `suspend fun uploadImage` 追加 |
| `backend/core/.../application/service/product/ProductService.kt` | `suspend fun imageUrl` 追加 |
| `backend/api/.../presentation/rpc/product/ProductRegisterController.kt` | `uploadImage` 実装 |
| `backend/api/.../presentation/rpc/product/ProductController.kt` | `imageUrl` 実装 |
| `backend/api/.../configuration/routing/RoutingConfiguration.kt` | ProductRegisterController に storageRepo 依存追加(必要時) |
| `frontend/src/commonMain/.../designsystem/atom/Thumb.kt` | `image: ImageBitmap?` 引数追加 |
| `frontend/src/commonMain/.../feature/inventory/data/InventoryRepository.kt` | `imageUrl` / `uploadImage` メソッド追加 |
| `frontend/src/webMain/.../App.kt` | ProductImageLoader 配線 |
| `frontend/.../feature/catalog/ui/ProductSettingsSheet.kt` | ImageField 差し込み + onUploadImage/onRemoveImage 配線 |
| `frontend/src/commonMain/composeResources/values/strings.xml` | 画像関連文言 |

---

# Stage 0: Garage インフラ + S3 クライアント配線

> このステージは TDD でなく「環境構築 + 手動疎通」。完了基準 = ローカルで Garage に put/get できること。

### Task 0.1: gradle に aws-sdk-kotlin s3 を追加

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `backend/core/build.gradle.kts`

- [ ] **Step 1: 最新版を確認**

Run: `curl -s "https://search.maven.org/solrsearch/select?q=g:aws.sdk.kotlin+AND+a:s3&core=gav&rows=1&wt=json" | head -c 400`
Expected: 最新 version(例 `1.x.y`)が取れる。取れない場合は https://github.com/awslabs/aws-sdk-kotlin/releases の最新安定版を使う。

- [ ] **Step 2: libs.versions.toml に追記**

`[versions]` に:
```toml
aws-sdk-kotlin = "1.5.40"   # Step 1 で確認した最新安定版に置換
```
`[libraries]` に:
```toml
aws-sdk-kotlin-s3 = { module = "aws.sdk.kotlin:s3", version.ref = "aws-sdk-kotlin" }
```

- [ ] **Step 3: backend/core/build.gradle.kts の dependencies に追記**

```kotlin
implementation(libs.aws.sdk.kotlin.s3)
```

- [ ] **Step 4: 依存解決を確認**

Run: `./gradlew :backend:core:dependencies --configuration runtimeClasspath | grep -i "aws.sdk.kotlin"`
Expected: `aws.sdk.kotlin:s3` が解決される。

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml backend/core/build.gradle.kts
git commit -m "build(backend): aws-sdk-kotlin s3 依存を追加"
```

### Task 0.2: compose に Garage + garage-init を追加

**Files:**
- Create: `docker/garage.toml`
- Create: `docker/garage-init.sh`
- Modify: `compose.yml`

- [ ] **Step 1: docker/garage.toml を作成**

```toml
metadata_dir = "/var/lib/garage/meta"
data_dir = "/var/lib/garage/data"
db_engine = "sqlite"
replication_factor = 1
rpc_bind_addr = "[::]:3901"
rpc_public_addr = "127.0.0.1:3901"
rpc_secret = "0000000000000000000000000000000000000000000000000000000000000000"

[s3_api]
s3_region = "garage"
api_bind_addr = "[::]:3900"
root_domain = ".s3.garage.localhost"

[admin]
api_bind_addr = "[::]:3903"
admin_token = "localadmintoken"
```
> ローカル用の固定 secret/token。本番は環境ごとに別途生成し repo に置かない。

- [ ] **Step 2: compose.yml に garage / garage-init サービスを追加(`volumes:` セクションの前)**

```yaml
  garage:
    image: "dxflrs/garage:v2.3.0"
    container_name: "mindstock-garage"
    volumes:
      - "./docker/garage.toml:/etc/garage.toml:ro"
      - "mindstock-garage-meta:/var/lib/garage/meta"
      - "mindstock-garage-data:/var/lib/garage/data"
    ports:
      - "3900:3900"
      - "3903:3903"
    restart: "unless-stopped"

  garage-init:
    image: "dxflrs/garage:v2.3.0"
    container_name: "mindstock-garage-init"
    depends_on:
      - garage
    volumes:
      - "./docker/garage-init.sh:/garage-init.sh:ro"
      - "./:/output"
    entrypoint: ["/bin/sh", "/garage-init.sh"]
    restart: "no"
```
そして既存の `volumes:` ブロックに追記:
```yaml
volumes:
  mindstock-pgdata:
  mindstock-garage-meta:
  mindstock-garage-data:
```

- [ ] **Step 3: docker/garage-init.sh を作成(冪等・`.env.garage` 出力。zitadel-init.sh に倣う)**

```sh
#!/bin/sh
set -eu

# garage CLI は同イメージに同梱。garage コンテナへ RPC で繋ぐ。
export GARAGE_RPC_HOST="garage:3901"
export GARAGE_RPC_SECRET="0000000000000000000000000000000000000000000000000000000000000000"
BUCKET="mindstock-images"
OUT="/output/.env.garage"

# garage 起動待ち(status が node を返すまで)
i=0
until garage status 2>/dev/null | grep -q "NO ROLE ASSIGNED\|$BUCKET\|HEALTHY\|ID"; do
  i=$((i+1)); [ "$i" -gt 60 ] && { echo "garage not ready"; exit 1; }
  sleep 2
done

NODE_ID="$(garage node id -q 2>/dev/null | cut -d@ -f1)"

# layout 未割当なら割り当て(冪等: 既に割当済なら apply で no-op/失敗を許容)
if ! garage layout show | grep -q "$NODE_ID"; then
  garage layout assign -z dc1 -c 1G "$NODE_ID"
  garage layout apply --version 1 || true
fi

# bucket 冪等作成
garage bucket info "$BUCKET" >/dev/null 2>&1 || garage bucket create "$BUCKET"

# key 冪等作成(既存なら info から取得)
if garage key info mindstock-key >/dev/null 2>&1; then
  KEYINFO="$(garage key info --show-secret mindstock-key)"
else
  KEYINFO="$(garage key create mindstock-key)"
  KEYINFO="$(garage key info --show-secret mindstock-key)"
fi
AK="$(echo "$KEYINFO" | grep -i 'Key ID' | awk '{print $NF}')"
SK="$(echo "$KEYINFO" | grep -i 'Secret key' | awk '{print $NF}')"

garage bucket allow --read --write --owner "$BUCKET" --key mindstock-key

# バケット CORS(presigned GET をブラウザから引くため・dev origin 許可)
garage bucket website --allow "$BUCKET" || true
cat > /tmp/cors.json <<JSON
{"CORSRules":[{"AllowedOrigins":["http://localhost:8080","http://localhost:8081"],"AllowedMethods":["GET"],"AllowedHeaders":["*"],"MaxAgeSeconds":3000}]}
JSON
# garage は PutBucketCors を S3 API で受ける。aws CLI 互換が無いので admin/API で設定するか、
# 起動後に backend or 手動で put-bucket-cors を流す(下記 Step 5 で検証)。

cat > "$OUT" <<EOF
STORAGE_ENDPOINT=http://localhost:3900
STORAGE_REGION=garage
STORAGE_BUCKET=$BUCKET
STORAGE_ACCESS_KEY=$AK
STORAGE_SECRET_KEY=$SK
EOF
echo "garage-init done: wrote $OUT"
```
> 注: garage CLI のサブコマンド/出力は v2.3.0 を前提。実行時に `garage --help` で `node id` / `key info --show-secret` / `bucket allow` の正確なフラグを確認し、ズレたら修正する(Step 4 が検証)。CORS は S3 `PutBucketCors` API でも設定可(Task 0.5 で aws-sdk-kotlin から流す方式に寄せてもよい)。

- [ ] **Step 4: 起動して .env.garage が生成されることを確認**

Run: `docker compose up -d garage && sleep 5 && docker compose up garage-init && cat .env.garage`
Expected: `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` を含む `.env.garage` が生成される。失敗時は garage CLI のフラグを `docker compose exec garage garage --help` で確認し Step 3 を修正。

- [ ] **Step 5: .gitignore に .env.garage を追加(.env.zitadel と同様の扱いか確認)**

Run: `grep -n "env.zitadel\|.env" .gitignore`
Expected: `.env.zitadel` が ignore されていれば `.env.garage` も同様に追記。

```bash
echo ".env.garage" >> .gitignore   # 既に .env* で包含されていれば不要
```

- [ ] **Step 6: Commit**

```bash
git add compose.yml docker/garage.toml docker/garage-init.sh .gitignore
git commit -m "infra(compose): Garage(S3互換) + garage-init を追加"
```

### Task 0.3: external.storage 設定 + S3Client 生成・DI provide

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/external/storage/StorageProperties.kt`
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/external/storage/StorageConfiguration.kt`
- Modify: `backend/api/src/main/resources/application.yaml`

- [ ] **Step 1: application.yaml に external.storage を追加(`external:` 配下)**

```yaml
  storage:
    endpoint: "$STORAGE_ENDPOINT:http://localhost:3900"
    region: "$STORAGE_REGION:garage"
    bucket: "$STORAGE_BUCKET:mindstock-images"
    access-key: "$STORAGE_ACCESS_KEY"
    secret-key: "$STORAGE_SECRET_KEY"
```

- [ ] **Step 2: modules リストに StorageConfiguration を追加(application.yaml の `ktor.application.modules`、`dependenciesConfigure` の前)**

```yaml
      - "net.brightroom.mindstock.configuration.external.storage.StorageConfigurationKt.storageConfigure"
```

- [ ] **Step 3: StorageProperties.kt を作成(ExposedDataSourceProperties に倣う)**

```kotlin
package net.brightroom.mindstock.configuration.external.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StorageProperties(
    @SerialName("endpoint") val endpoint: String,
    @SerialName("region") val region: String,
    @SerialName("bucket") val bucket: String,
    @SerialName("access-key") val accessKey: String,
    @SerialName("secret-key") val secretKey: String,
)
```

- [ ] **Step 4: StorageConfiguration.kt を作成(S3Client 生成 + bucket 名も DI に出す)**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.configuration.external.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import kotlinx.coroutines.runBlocking

/** Garage 接続先のバケット名を DI で配るための薄い holder。 */
data class StorageBucket(val name: String)

fun Application.storageConfigure() {
    val cfg = environment.config.config("external.storage")
    val endpoint = cfg.property("endpoint").getString()
    val region = cfg.property("region").getString()
    val bucket = cfg.property("bucket").getString()
    val accessKey = cfg.property("access-key").getString()
    val secretKey = cfg.property("secret-key").getString()

    val s3 = S3Client {
        this.region = region
        endpointUrl = Url.parse(endpoint)
        forcePathStyle = true // Garage/MinIO は path-style 必須
        credentialsProvider = object : CredentialsProvider {
            override suspend fun resolve(attributes: aws.smithy.kotlin.runtime.collections.Attributes) =
                Credentials(accessKeyId = accessKey, secretAccessKey = secretKey)
        }
    }

    monitor.subscribe(ApplicationStopped) { runBlocking { s3.close() } }

    dependencies {
        provide<S3Client> { s3 }
        provide<StorageBucket> { StorageBucket(bucket) }
    }
}
```
> 注: `CredentialsProvider` / `Url.parse` / `forcePathStyle` の正確な import・シグネチャは aws-sdk-kotlin の版で微差あり。コンパイルエラー時は `S3Client { }` ビルダの補完で確認(`StaticCredentialsProvider` が使える版ならそれを使う)。

- [ ] **Step 5: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL。import/シグネチャのズレは Step 4 の注記に従い修正。

- [ ] **Step 6: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/external/storage backend/api/src/main/resources/application.yaml
git commit -m "feat(backend): S3(Garage)クライアント生成と external.storage 設定"
```

---

# Stage 1: ドメイン VO

### Task 1.1: RawImageUpload VO

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/RawImageUpload.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/RawImageUploadTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RawImageUploadTest {
    @Test
    fun `空バイト列は拒否`() {
        shouldThrow<IllegalArgumentException> { RawImageUpload(ByteArray(0)) }
    }

    @Test
    fun `非空バイト列はそのまま保持`() {
        val bytes = byteArrayOf(1, 2, 3)
        RawImageUpload(bytes).invoke() shouldBe bytes
    }
}
```

- [ ] **Step 2: 失敗を確認**

Run: `./gradlew :domain:compileKotlinJvm 2>&1 | tail -5` (型が無くコンパイル不可 = 期待どおり)
Expected: `RawImageUpload` 未定義でコンパイル失敗。

- [ ] **Step 3: RawImageUpload.kt を実装**

```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.image

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** クライアントがアップロードした原画像バイト列。application 公開 API で primitive ByteArray を晒さないための VO。 */
@Serializable
@JvmInline
value class RawImageUpload(
    private val value: ByteArray,
) {
    init {
        require(value.isNotEmpty()) { "RawImageUpload must not be empty" }
    }

    operator fun invoke(): ByteArray = value
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "*RawImageUploadTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/RawImageUpload.kt domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/RawImageUploadTest.kt
git commit -m "feat(domain): RawImageUpload VO を追加"
```

### Task 1.2: ImageUrl VO

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/ImageUrl.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/ImageUrlTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ImageUrlTest {
    @Test
    fun `空文字は拒否`() {
        shouldThrow<IllegalArgumentException> { ImageUrl("") }
    }

    @Test
    fun `URL 文字列を保持`() {
        ImageUrl("https://x/y").invoke() shouldBe "https://x/y"
    }
}
```

- [ ] **Step 2: 失敗を確認**

Run: `./gradlew :domain:jvmTest --tests "*ImageUrlTest*" 2>&1 | tail -5`
Expected: コンパイル失敗(未定義)。

- [ ] **Step 3: ImageUrl.kt を実装**

```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.image

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** presigned GET URL。imageUrl RPC の戻り値 VO。 */
@Serializable
@JvmInline
value class ImageUrl(
    private val value: String,
) {
    init {
        require(value.isNotBlank()) { "ImageUrl must not be blank" }
    }

    operator fun invoke(): String = value

    override fun toString(): String = value
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "*ImageUrlTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/ImageUrl.kt domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/ImageUrlTest.kt
git commit -m "feat(domain): ImageUrl VO を追加"
```

---

# Stage 2: 画像処理(純関数)

### Task 2.1: ImageProcessor(リサイズ + JPEG + sha256)

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/storage/image/ImageProcessor.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/storage/image/ImageProcessorTest.kt`

> 純関数(S3 不要)。`@Tags("integration")` を付けず通常 unit test で回す。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.infrastructure.storage.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageProcessorTest : FunSpec({
    fun pngBytes(w: Int, h: Int): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return out.toByteArray()
    }

    test("大きい画像は最大辺 512px 以内の JPEG に縮小される") {
        val processed = ImageProcessor.process(pngBytes(2000, 1000))
        val read = ImageIO.read(processed.bytes.inputStream())
        maxOf(read.width, read.height) shouldBeLessThanOrEqual 512
    }

    test("ref は sha256 hex(64桁)で処理後バイトに対応") {
        val processed = ImageProcessor.process(pngBytes(100, 100))
        processed.ref shouldHaveLength 64
    }

    test("同一入力は同一 ref(content-addressed)") {
        val a = ImageProcessor.process(pngBytes(100, 100))
        val b = ImageProcessor.process(pngBytes(100, 100))
        a.ref shouldBe b.ref
    }

    test("画像でないバイト列は IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> { ImageProcessor.process(byteArrayOf(1, 2, 3, 4)) }
    }
})
```

- [ ] **Step 2: 失敗を確認**

Run: `./gradlew :backend:core:test --tests "*ImageProcessorTest*" 2>&1 | tail -5`
Expected: コンパイル失敗(未定義)。

- [ ] **Step 3: ImageProcessor.kt を実装**

```kotlin
package net.brightroom.mindstock.infrastructure.storage.image

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** 処理後の JPEG バイトと、その sha256 hex(= 保存キー)。 */
class ProcessedImage(val bytes: ByteArray, val ref: String)

/** 原画像バイト列を decode→最大辺 512px に縮小→JPEG(品質 0.85)再エンコード→sha256。S3 非依存の純処理。 */
object ImageProcessor {
    private const val MAX_EDGE = 512
    private const val JPEG_QUALITY = 0.85f

    fun process(raw: ByteArray): ProcessedImage {
        val src = ImageIO.read(raw.inputStream())
            ?: throw IllegalArgumentException("not a decodable image")
        val scaled = resize(src)
        val jpeg = encodeJpeg(scaled)
        return ProcessedImage(jpeg, sha256Hex(jpeg))
    }

    private fun resize(src: BufferedImage): BufferedImage {
        val longEdge = maxOf(src.width, src.height)
        if (longEdge <= MAX_EDGE) return toRgb(src)
        val scale = MAX_EDGE.toDouble() / longEdge
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val dst = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = dst.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return dst
    }

    // JPEG は透過非対応。alpha を含む入力を白背景の RGB に落とす。
    private fun toRgb(src: BufferedImage): BufferedImage {
        if (src.type == BufferedImage.TYPE_INT_RGB) return src
        val dst = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = dst.createGraphics()
        g.drawImage(src, 0, 0, java.awt.Color.WHITE, null)
        g.dispose()
        return dst
    }

    private fun encodeJpeg(img: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { ios ->
            writer.output = ios
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = JPEG_QUALITY
            }
            writer.write(null, IIOImage(img, null, null), param)
        }
        writer.dispose()
        return out.toByteArray()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "*ImageProcessorTest*"`
Expected: PASS(4 件)

- [ ] **Step 5: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/storage/image/ImageProcessor.kt backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/storage/image/ImageProcessorTest.kt
git commit -m "feat(backend): 画像リサイズ/JPEG/sha256 の純処理を追加"
```

---

# Stage 3: storage repository(Garage 連携)

### Task 3.1: ProductImageStorageRepository interface

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product/ProductImageStorageRepository.kt`

- [ ] **Step 1: interface を作成**

```kotlin
package net.brightroom.mindstock.application.repository.product

import net.brightroom.mindstock.domain.model.inventory.product.image.ImageRef
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageUrl
import net.brightroom.mindstock.domain.model.inventory.product.image.RawImageUpload

/** 商品画像の実体ストレージ(Garage/S3)。 */
interface ProductImageStorageRepository {
    /** 原バイトを処理(縮小/JPEG/sha256)して put し、保存キー(ImageRef)を返す。 */
    suspend fun store(upload: RawImageUpload): ImageRef

    /** ref に対する presigned GET URL(有効期限付き)を発行する。 */
    suspend fun presignedUrl(ref: ImageRef): ImageUrl
}
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product/ProductImageStorageRepository.kt
git commit -m "feat(backend): ProductImageStorageRepository interface を追加"
```

### Task 3.2: ProductImageStorageDataSource(integration test)

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/storage/image/ProductImageStorageDataSource.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/storage/image/ProductImageStorageDataSourceTest.kt`

> S3 を叩くため `@Tags("integration")`。ローカル compose の Garage に `TEST_STORAGE_*` env で接続。

- [ ] **Step 1: 実装を作成**

```kotlin
@file:OptIn(aws.smithy.kotlin.runtime.time.ExperimentalApi::class)

package net.brightroom.mindstock.infrastructure.storage.image

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.smithy.kotlin.runtime.content.ByteStream
import net.brightroom.mindstock.application.repository.product.ProductImageStorageRepository
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageRef
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageUrl
import net.brightroom.mindstock.domain.model.inventory.product.image.RawImageUpload
import kotlin.time.Duration.Companion.hours

class ProductImageStorageDataSource(
    private val s3: S3Client,
    private val bucket: String,
) : ProductImageStorageRepository {

    override suspend fun store(upload: RawImageUpload): ImageRef {
        val processed = ImageProcessor.process(upload.invoke())
        s3.putObject(
            PutObjectRequest {
                this.bucket = this@ProductImageStorageDataSource.bucket
                key = processed.ref
                body = ByteStream.fromBytes(processed.bytes)
                contentType = "image/jpeg"
            },
        )
        return ImageRef(processed.ref)
    }

    override suspend fun presignedUrl(ref: ImageRef): ImageUrl {
        val presigned = s3.presignGetObject(
            GetObjectRequest {
                bucket = this@ProductImageStorageDataSource.bucket
                key = ref.invoke()
            },
            1.hours,
        )
        return ImageUrl(presigned.url.toString())
    }
}
```
> 注: `presignGetObject` の import パス(`aws.sdk.kotlin.services.s3.presigners.presignGetObject`)と戻り値 `HttpRequest.url` は版差あり。コンパイルエラー時は presigner パッケージを補完で確認。

- [ ] **Step 2: 失敗する integration テストを書く**

```kotlin
package net.brightroom.mindstock.infrastructure.storage.image

import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.net.url.Url
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import net.brightroom.mindstock.domain.model.inventory.product.image.RawImageUpload

@Tags("integration")
class ProductImageStorageDataSourceTest : FunSpec({
    val endpoint = System.getenv("TEST_STORAGE_ENDPOINT") ?: "http://localhost:3900"
    val bucket = System.getenv("TEST_STORAGE_BUCKET") ?: "mindstock-images"
    val ak = System.getenv("TEST_STORAGE_ACCESS_KEY") ?: error("TEST_STORAGE_ACCESS_KEY required")
    val sk = System.getenv("TEST_STORAGE_SECRET_KEY") ?: error("TEST_STORAGE_SECRET_KEY required")

    val s3 = S3Client {
        region = "garage"
        endpointUrl = Url.parse(endpoint)
        forcePathStyle = true
        credentialsProvider = object : CredentialsProvider {
            override suspend fun resolve(attributes: Attributes) =
                Credentials(accessKeyId = ak, secretAccessKey = sk)
        }
    }
    val ds = ProductImageStorageDataSource(s3, bucket)

    fun png(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }

    test("store → presignedUrl → 直 GET で同一バイトが返る") {
        val ref = ds.store(RawImageUpload(png()))
        val url = ds.presignedUrl(ref)
        val fetched = HttpClient().use { it.get(url.invoke()).readRawBytes() }
        ImageIO.read(fetched.inputStream()).width shouldBe 100
    }
})
```

- [ ] **Step 3: テストが失敗することを確認(Garage 未起動 or env 未設定で)**

Run: `./gradlew :backend:core:test --tests "*ProductImageStorageDataSourceTest*"`
Expected: `@Tags("integration")` 除外で SKIP されるか、env 無しで error。これは想定どおり(integ は別タスク)。

- [ ] **Step 4: Garage を起動し env を入れて integration を回す**

```bash
docker compose up -d garage && docker compose up garage-init
set -a; . ./.env.garage; set +a
export TEST_STORAGE_ENDPOINT=$STORAGE_ENDPOINT TEST_STORAGE_BUCKET=$STORAGE_BUCKET \
       TEST_STORAGE_ACCESS_KEY=$STORAGE_ACCESS_KEY TEST_STORAGE_SECRET_KEY=$STORAGE_SECRET_KEY
./gradlew :backend:core:test --tests "*ProductImageStorageDataSourceTest*" -Dkotest.tags.include=integration
```
Expected: PASS。失敗時は presign の import / put の API シグネチャを修正。

- [ ] **Step 5: backend/core の test task が integration env を受け取れるようにする(必要なら build.gradle.kts に env 受け渡しを追加)**

`backend/core/build.gradle.kts` の test 設定に(`backend/api` の integrationTest を参考に):
```kotlin
tasks.test {
    listOf("TEST_STORAGE_ENDPOINT", "TEST_STORAGE_BUCKET", "TEST_STORAGE_ACCESS_KEY", "TEST_STORAGE_SECRET_KEY")
        .forEach { key -> System.getenv(key)?.let { environment(key, it) } }
}
```
> ktor client(test 用)が backend/core の testImplementation に無ければ追加: `testImplementation(ktorLib.client.cio)`(api モジュールと同じ catalog)。

- [ ] **Step 6: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/storage/image/ProductImageStorageDataSource.kt backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/storage/image/ProductImageStorageDataSourceTest.kt backend/core/build.gradle.kts
git commit -m "feat(backend): Garage put + presigned GET の DataSource を追加"
```

### Task 3.3: DI に ProductImageStorageRepository を provide

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt`

- [ ] **Step 1: provide を追加(Repository 群の近く)**

```kotlin
provide<ProductImageStorageRepository> {
    ProductImageStorageDataSource(resolve(), resolve<StorageBucket>().name)
}
```
import を追加(`ProductImageStorageRepository` / `ProductImageStorageDataSource` / `StorageBucket`)。

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt
git commit -m "feat(backend): ProductImageStorageRepository を DI に登録"
```

---

# Stage 4: application service

### Task 4.1: ProductRegisterService.uploadImage

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterService.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterServiceUploadImageTest.kt`

- [ ] **Step 1: 失敗するテストを書く(storage/repos は mockk)**

```kotlin
package net.brightroom.mindstock.application.service.product

import io.kotest.core.spec.style.FunSpec
import io.mockk.*
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.application.repository.product.*
import net.brightroom.mindstock.domain.model.inventory.product.image.*
// ... 既存テストの import を参照(Product/ProductId/ResidentId 等)

class ProductRegisterServiceUploadImageTest : FunSpec({
    test("uploadImage は store した ref を product.changeImage で revision に反映する") {
        runTest {
            val productRepo = mockk<ProductRepository>()
            val registerRepo = mockk<ProductRegisterRepository>(relaxed = true)
            val stockRepo = mockk<StockRepository>()
            val householdRepo = mockk<HouseholdRepository>(relaxed = true)
            val storage = mockk<ProductImageStorageRepository>()

            val productId = /* テスト用 ProductId */ TODO("既存テストの生成 util を流用")
            val actor = /* ResidentId */ TODO()
            val product = /* Product (image=None) */ TODO()
            val ref = ImageRef("a".repeat(64))

            // authorizeProduct が叩く householdOf 等を stub(既存テストの authorize stub を流用)
            every { productRepo.findById(productId) } returns product
            coEvery { storage.store(any()) } returns ref

            val service = ProductRegisterService(productRepo, registerRepo, stockRepo, householdRepo, storage)
            service.uploadImage(productId, RawImageUpload(byteArrayOf(1, 2, 3)), actor)

            coVerify { storage.store(any()) }
            verify { registerRepo.appendRevision(match { it.image == ProductImage.Stored(ref) }) }
        }
    }
})
```
> 注: `ProductId` / `ResidentId` / `Product` の生成は既存の `ProductRegisterServiceTest` のヘルパを流用。authorize 経路の stub も既存テストに合わせる。

- [ ] **Step 2: 失敗を確認**

Run: `./gradlew :backend:core:test --tests "*ProductRegisterServiceUploadImageTest*" 2>&1 | tail -5`
Expected: コンパイル失敗(`uploadImage` 未定義・コンストラクタに storage 引数なし)。

- [ ] **Step 3: ProductRegisterService にコンストラクタ依存と suspend メソッドを追加**

コンストラクタに `private val imageStorage: ProductImageStorageRepository` を追加。メソッド:
```kotlin
suspend fun uploadImage(productId: ProductId, upload: RawImageUpload, actor: ResidentId) {
    authorizeProduct(productId, actor)
    val ref = imageStorage.store(upload)
    val product = productRepository.findById(productId)
    productRegisterRepository.appendRevision(product.changeImage(ProductImage.Stored(ref)))
}
```
import: `RawImageUpload` / `ProductImage` / `ProductImageStorageRepository`。

- [ ] **Step 4: DI のコンストラクタ呼び出しを更新**

`DependenciesConfiguration.kt` の `provide<ProductRegisterService>` に `resolve()`(storage)を 1 つ追加:
```kotlin
provide<ProductRegisterService> { ProductRegisterService(resolve(), resolve(), resolve(), resolve(), resolve()) }
```

- [ ] **Step 5: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "*ProductRegisterServiceUploadImageTest*"`
Expected: PASS

- [ ] **Step 6: 既存の ProductRegisterServiceTest がコンストラクタ変更で壊れていないか確認・修正**

Run: `./gradlew :backend:core:test --tests "*ProductRegisterServiceTest*"`
Expected: PASS(壊れていれば storage モック引数を追加)。

- [ ] **Step 7: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterService.kt backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterServiceUploadImageTest.kt backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt
git commit -m "feat(backend): ProductRegisterService.uploadImage を追加"
```

### Task 4.2: ProductService.imageUrl

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductService.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/product/ProductServiceImageUrlTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.application.service.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.inventory.product.image.*

class ProductServiceImageUrlTest : FunSpec({
    test("Stored 画像は presigned URL を返す") {
        runTest {
            val productRepo = mockk<ProductRepository>()
            val storage = mockk<ProductImageStorageRepository>()
            val ref = ImageRef("b".repeat(64))
            val productId = TODO("ProductId")
            val product = TODO("Product with image = ProductImage.Stored(ref)")
            every { productRepo.findById(productId) } returns product
            coEvery { storage.presignedUrl(ref) } returns ImageUrl("https://x/y")

            val service = ProductService(/* stockRepo */ mockk(), productRepo, /* householdRepo */ mockk(), storage)
            service.imageUrl(productId).invoke() shouldBe "https://x/y"
        }
    }

    test("None 画像は ResourceNotFoundException") {
        runTest {
            val productRepo = mockk<ProductRepository>()
            val storage = mockk<ProductImageStorageRepository>()
            val productId = TODO("ProductId")
            val product = TODO("Product with image = ProductImage.None")
            every { productRepo.findById(productId) } returns product
            val service = ProductService(mockk(), productRepo, mockk(), storage)
            shouldThrow<ResourceNotFoundException> { service.imageUrl(productId) }
        }
    }
})
```

- [ ] **Step 2: 失敗を確認**

Run: `./gradlew :backend:core:test --tests "*ProductServiceImageUrlTest*" 2>&1 | tail -5`
Expected: コンパイル失敗。

- [ ] **Step 3: ProductService に依存と suspend メソッドを追加**

コンストラクタに `private val imageStorage: ProductImageStorageRepository` を追加。メソッド:
```kotlin
suspend fun imageUrl(productId: ProductId): ImageUrl {
    val product = productRepository.findById(productId)
    return when (val image = product.image) {
        is ProductImage.Stored -> imageStorage.presignedUrl(image.ref)
        ProductImage.None -> throw ResourceNotFoundException("product has no image: $productId")
    }
}
```
> 認可: imageUrl は世帯メンバーなら誰でも閲覧可。既存 `list` 同様に householdId 経由の member チェックを通す形にするか、productId からの参照閲覧を許すか。**productId は世帯一意で、表示は member 全員に許す**ため、ここでは追加の household 認可を課さない(presigned URL 自体が短命。Controller の requireRegistered で認証は担保)。この方針は spec の「registered ガード」と一致。

- [ ] **Step 4: DI のコンストラクタ呼び出しを更新**

```kotlin
provide<ProductService> { ProductService(resolve(), resolve(), resolve(), resolve()) }
```

- [ ] **Step 5: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "*ProductServiceImageUrlTest*"`
Expected: PASS

- [ ] **Step 6: 既存 ProductServiceTest の修正(コンストラクタ変更)**

Run: `./gradlew :backend:core:test --tests "*ProductServiceTest*"`
Expected: PASS(壊れていれば storage モック追加)。

- [ ] **Step 7: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductService.kt backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/product/ProductServiceImageUrlTest.kt backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt
git commit -m "feat(backend): ProductService.imageUrl を追加"
```

---

# Stage 5: presentation(RPC)

### Task 5.1: RPC interface に uploadImage / imageUrl を追加

**Files:**
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/UploadImageRequest.kt`
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/ProductRegisterRpcService.kt`
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/ProductRpcService.kt`

- [ ] **Step 1: UploadImageRequest.kt を作成**

```kotlin
package net.brightroom.mindstock.rpc.product

import kotlinx.serialization.Serializable

/** 画像アップロードの wire 型。原画像を base64 で運ぶ(WS-RPC は JSON 文字列で安全)。 */
@Serializable
data class UploadImageRequest(
    val base64: String,
)
```

- [ ] **Step 2: ProductRegisterRpcService に uploadImage を追加**

```kotlin
/** 画像をアップロード(UC22, owner のみ)。原画像を base64 で送り、サーバが検証/縮小/保存する。 */
suspend fun uploadImage(
    productId: ProductId,
    request: UploadImageRequest,
): RpcResult<Unit, RpcError>
```

- [ ] **Step 3: ProductRpcService に imageUrl を追加**

```kotlin
/** 商品画像の presigned GET URL を取得。画像未設定は NotFound。 */
suspend fun imageUrl(productId: ProductId): RpcResult<ImageUrl, RpcError>
```
import: `net.brightroom.mindstock.domain.model.inventory.product.image.ImageUrl` / `ProductId`。

- [ ] **Step 4: コンパイル確認**

Run: `./gradlew :rpc:compileKotlinJvm :rpc:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL(両ターゲット)。

- [ ] **Step 5: Commit**

```bash
git add rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/
git commit -m "feat(rpc): uploadImage / imageUrl を RPC interface に追加"
```

### Task 5.2: Controller 実装

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductRegisterController.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductController.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductImageControllerTest.kt`

- [ ] **Step 1: ProductRegisterController.uploadImage を実装**

```kotlin
override suspend fun uploadImage(
    productId: ProductId,
    request: UploadImageRequest,
): RpcResult<Unit, RpcError> =
    requireRegistered(session) { residentId ->
        val raw = try {
            kotlin.io.encoding.Base64.decode(request.base64)
        } catch (e: IllegalArgumentException) {
            return@requireRegistered RpcResult.Err(RpcError.BadRequest(field = "base64", reason = "invalid base64"))
        }
        if (raw.size > MAX_UPLOAD_BYTES) {
            return@requireRegistered RpcResult.Err(RpcError.BadRequest(field = "base64", reason = "image too large"))
        }
        productRegisterService.uploadImage(productId, RawImageUpload(raw), residentId)
        RpcResult.Ok(Unit)
    }
```
クラス上部に `private companion object { const val MAX_UPLOAD_BYTES = 8 * 1024 * 1024 }`。
`@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)` をファイル先頭に。
import: `RawImageUpload` / `UploadImageRequest`。
> IAE(ImageProcessor の decode 不能)は既存の SessionGuard 例外翻訳(IAE→BadRequest)で吸収される。

- [ ] **Step 2: ProductController.imageUrl を実装**

```kotlin
override suspend fun imageUrl(productId: ProductId): RpcResult<ImageUrl, RpcError> =
    requireRegistered(session) { _ ->
        RpcResult.Ok(productService.imageUrl(productId))
    }
```
import: `ImageUrl`。`ResourceNotFoundException`→NotFound は SessionGuard で翻訳される(既存)。

- [ ] **Step 3: integration テストを書く(既存 ProductRegisterControllerTest のセットアップを流用)**

```kotlin
@Tags("integration")
class ProductImageControllerTest : FunSpec({
    // 既存 Controller テストの testApplication + 認証 + seed パターンを流用。
    test("uploadImage 後に imageUrl が presigned URL を返し、その URL を GET すると画像が引ける") {
        // 1. 商品を adopt/addCustom で作る
        // 2. uploadImage(productId, UploadImageRequest(base64 of png))
        // 3. imageUrl(productId) -> RpcResult.Ok(ImageUrl)
        // 4. HttpClient で URL を GET -> 200 + JPEG bytes
    }
    test("不正 base64 は BadRequest") { /* ... */ }
    test("画像未設定の商品の imageUrl は NotFound") { /* ... */ }
})
```
> 既存の Controller 統合テスト(`ProductRegisterControllerTest` 等)の testApplication 起動・JWKS mockk・seed の形をそのまま使う。Garage への接続が要るため `@Tags("integration")` とし、DI が StorageConfiguration 経由で S3Client を解決できるよう testApplication の config に `external.storage.*` を渡す(env or test config)。

- [ ] **Step 4: Routing で ProductController/ProductRegisterController が storage を解決できるか確認**

`RoutingConfiguration.kt` の registerService で渡す service は既に DI 済(ProductService/ProductRegisterService が storage を内包)。Controller 自体に新しい依存は増えない(service 経由)。コンパイル確認:
Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: unit テスト範囲(非 integration)を回す**

Run: `./gradlew :backend:api:test`
Expected: 既存テスト PASS(integration は除外される)。

- [ ] **Step 6: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductImageControllerTest.kt
git commit -m "feat(backend): uploadImage / imageUrl の Controller を実装"
```

---

# Stage 6: backend E2E 検証(手動)

### Task 6.1: 実 backend で upload→imageUrl→fetch を通す

> コードは書かない。Garage + backend を起動し、E2E が通ることを目視確認する。

- [ ] **Step 1: Garage と backend を起動**

```bash
docker compose up -d postgres zitadel garage
docker compose up zitadel-init garage-init
set -a; . ./.env.zitadel; . ./.env.garage; set +a
export STORAGE_ENDPOINT=$STORAGE_ENDPOINT STORAGE_BUCKET=$STORAGE_BUCKET \
       STORAGE_ACCESS_KEY=$STORAGE_ACCESS_KEY STORAGE_SECRET_KEY=$STORAGE_SECRET_KEY
./gradlew :backend:api:run
```
Expected: 起動ログにエラーなし(S3Client 生成・DI 解決が通る)。

- [ ] **Step 2: integration テストで E2E を確認(Task 5.2 の ProductImageControllerTest を integration で実行)**

```bash
./gradlew :backend:api:integrationTest --tests "*ProductImageControllerTest*"
```
Expected: PASS。これが通れば upload→保存→presign→直GET の E2E が成立。

- [ ] **Step 3: Garage に object が入っていることを確認**

```bash
docker compose exec garage garage bucket info mindstock-images
```
Expected: object 数が 1 以上。

---

# Stage 7: frontend 表示(Thumb 画像化 + ロード経路)

### Task 7.1: Thumb に image 引数を追加

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/Thumb.kt`

- [ ] **Step 1: image 引数と分岐を追加**

`Thumb` のシグネチャに `image: ImageBitmap? = null` を追加。冒頭で分岐:
```kotlin
if (image != null) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(radius))
            .border(BorderStroke(1.dp, scheme.outlineVariant), RoundedCornerShape(radius)),
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
    return
}
// 既存のハッチ + アイコン描画はそのまま
```
import: `androidx.compose.foundation.Image` / `androidx.compose.ui.graphics.ImageBitmap` / `androidx.compose.ui.layout.ContentScale` / `androidx.compose.foundation.layout.fillMaxSize`。

- [ ] **Step 2: コンパイル確認(既存 8 呼び出し箇所は image 省略でデフォルト null)**

Run: `./gradlew :frontend:compileKotlinJs`
Expected: BUILD SUCCESSFUL(既存呼び出しは影響なし)。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/Thumb.kt
git commit -m "feat(frontend): Thumb に画像表示(ImageBitmap)対応を追加"
```

### Task 7.2: InventoryRepository に imageUrl / uploadImage を追加

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/data/InventoryRepository.kt`

- [ ] **Step 1: メソッドを追加(既存メソッドの `service().method().toOutcome()` パターンに倣う)**

```kotlin
suspend fun imageUrl(productId: ProductId): RpcOutcome<ImageUrl> =
    productService().imageUrl(productId).toOutcome()

suspend fun uploadImage(productId: ProductId, base64: String): RpcOutcome<Unit> =
    productRegisterService().uploadImage(productId, UploadImageRequest(base64)).toOutcome()
```
> `productService()` / `productRegisterService()` は既存の遅延 service アクセサに合わせる(無ければ既存パターンで追加)。import: `ImageUrl` / `UploadImageRequest`。

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/data/InventoryRepository.kt
git commit -m "feat(frontend): InventoryRepository に imageUrl/uploadImage を追加"
```

### Task 7.3: ProductImageLoader(presigned fetch + decode + cache)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/image/ProductImageLoader.kt`
- Modify: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt`

- [ ] **Step 1: ProductImageLoader を作成**

```kotlin
package net.brightroom.mindstock.frontend.core.image

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageUrl
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome

/** productId → ImageBitmap のメモリキャッシュ。imageUrl RPC → Garage 直 fetch → decode。 */
class ProductImageLoader(
    private val http: HttpClient,
    private val fetchUrl: suspend (ProductId) -> RpcOutcome<ImageUrl>,
) {
    private val cache = mutableMapOf<ProductId, ImageBitmap>()
    private val mutex = Mutex()

    suspend fun load(productId: ProductId): ImageBitmap? {
        cache[productId]?.let { return it }
        return mutex.withLock {
            cache[productId]?.let { return it }
            val url = when (val out = fetchUrl(productId)) {
                is RpcOutcome.Success -> out.value
                is RpcOutcome.Failure -> return null // NotFound 含む → アイコン fallback
            }
            val bytes = runCatching { http.get(url.invoke()).readRawBytes() }.getOrNull() ?: return null
            val bitmap = runCatching { bytes.decodeToImageBitmap() }.getOrNull() ?: return null
            cache[productId] = bitmap
            bitmap
        }
    }
}

/** Stored 画像を持つ商品だけ遅延ロードして ImageBitmap を返す composable ヘルパ。 */
@Composable
fun rememberProductImage(loader: ProductImageLoader, productId: ProductId, hasStoredImage: Boolean): ImageBitmap? {
    if (!hasStoredImage) return null
    var bitmap by remember(productId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(productId) { bitmap = loader.load(productId) }
    return bitmap
}
```
> `decodeToImageBitmap()` は Compose Multiplatform 1.7+ の common 拡張(JPEG/PNG・wasmJs 可)。本プロジェクトは 1.11.0 で利用可。import パスが違えば `org.jetbrains.compose.resources` 系を補完で確認。

- [ ] **Step 2: App.kt で ProductImageLoader を生成して画面ツリーに渡す**

`App.kt` の `HttpClient` / `InventoryRepository` 生成箇所付近で:
```kotlin
val imageLoader = remember(repository) {
    ProductImageLoader(http) { productId -> repository.imageUrl(productId) }
}
```
これを `CompositionLocalProvider(LocalProductImageLoader provides imageLoader) { ... }` で配るか、既存の画面に props で渡す。`LocalProductImageLoader` を `core/image/` に定義:
```kotlin
val LocalProductImageLoader = staticCompositionLocalOf<ProductImageLoader> { error("not provided") }
```

- [ ] **Step 3: 主要な Thumb 呼び出し箇所を画像対応にする(ProductMasterScreen の MasterStockRow など)**

例(`ProductMasterScreen.kt` line 181):
```kotlin
val loader = LocalProductImageLoader.current
val img = rememberProductImage(loader, stock.product.id(), stock.product.image is ProductImage.Stored)
Thumb(icon = glyphForProductName(stock.product.name()), size = 46.dp, image = img)
```
> まずは ProductMaster(マスタ一覧)と ProductDetail の Thumb を対応。在庫ホームの ProductCard 等は段階的に。`stock.product.id()` の公開アクセサが無ければ既存の取得方法に合わせる。

- [ ] **Step 4: コンパイル + dev server で目視**

Run: `./gradlew :frontend:compileKotlinJs`
Expected: BUILD SUCCESSFUL
その後 Stage 6 の backend を起動した状態で `./gradlew :frontend:jsBrowserDevelopmentRun --continuous`、画像をアップロード済みの商品で Thumb に画像が出ることを確認(アップロード UI は次 Stage。検証は backend で seed した画像 or 一時的に他の手段で put した ref を持つ商品で)。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/image/ frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/ProductMasterScreen.kt
git commit -m "feat(frontend): presigned URL 直 fetch + decode で商品画像を表示"
```

---

# Stage 8: frontend アップロード(ImageField + Wasm ピッカー)

### Task 8.1: Wasm 画像ピッカー(expect/actual)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/image/ImagePicker.kt`(expect)
- Create: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/core/image/ImagePicker.web.kt`(actual)

- [ ] **Step 1: expect 宣言**

```kotlin
package net.brightroom.mindstock.frontend.core.image

/** 端末から画像ファイルを選ばせ、base64(原バイト)で返す。キャンセル時は null。 */
expect suspend fun pickImageAsBase64(): String?
```

- [ ] **Step 2: webMain actual を実装(DOM input + FileReader)**

```kotlin
package net.brightroom.mindstock.frontend.core.image

import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get
import kotlin.coroutines.resume

actual suspend fun pickImageAsBase64(): String? = suspendCancellableCoroutine { cont ->
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = "image/*"
    input.onchange = {
        val file = input.files?.get(0)
        if (file == null) {
            cont.resume(null)
        } else {
            val reader = FileReader()
            reader.onload = {
                // readAsDataURL は "data:<mime>;base64,<payload>"。payload だけ返す。
                val result = reader.result as String
                cont.resume(result.substringAfter(","))
            }
            reader.onerror = { cont.resume(null) }
            reader.readAsDataURL(file)
        }
    }
    input.click()
}
```
> wasmJs の DOM/FileReader interop は kotlinx-browser + Kotlin/Wasm の JS interop で。`org.w3c.files` の API シグネチャは Kotlin/Wasm stdlib の版で微差あり、コンパイルエラー時は補完で確認。jsMain/wasmJsMain が分かれている場合は両方に actual を置く(webMain が共通なら 1 つ)。

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinJs :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL(両ターゲットで actual 解決)。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/image/ImagePicker.kt frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/core/image/ImagePicker.web.kt
git commit -m "feat(frontend): Wasm 画像ピッカー(input type=file)を追加"
```

### Task 8.2: ImageField composable + strings

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/ImageField.kt`
- Modify: `frontend/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: strings を追加**

```xml
<string name="image_field_add">画像を追加</string>
<string name="image_field_change">画像を変更</string>
<string name="image_field_remove">削除</string>
<string name="image_field_hint">正方形がおすすめ。未設定ならアイコンを表示します。</string>
```

- [ ] **Step 2: ImageField を作成(mock screens-master.jsx:ImageField 忠実)**

```kotlin
@Composable
fun ImageField(
    image: ImageBitmap?,
    fallbackIcon: AppIconName,
    onPick: () -> Unit,     // ピッカー起動(呼び出し側で pickImageAsBase64 → uploadImage)
    onRemove: () -> Unit,   // changeImage(None)
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Thumb(icon = fallbackIcon, size = 64.dp, radius = 16.dp, image = image)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(
                    text = stringResource(if (image != null) Res.string.image_field_change else Res.string.image_field_add),
                    variant = AppButtonVariant.Soft, // 既存 variant 名に合わせる
                    leadingIcon = AppIconName.Plus,
                    onClick = onPick,
                )
                if (image != null) {
                    AppButton(
                        text = stringResource(Res.string.image_field_remove),
                        variant = AppButtonVariant.Ghost,
                        leadingIcon = AppIconName.Trash,
                        onClick = onRemove,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            AppText(stringResource(Res.string.image_field_hint), /* 11.5px faint スタイルに合わせる */)
        }
    }
}
```
> `AppButton` / `AppButtonVariant` / `AppText` / `AppIconName` の正確な API は既存 atom に合わせる(catalog の他シートの利用例を参照)。

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/ImageField.kt frontend/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(frontend): ImageField composable と文言を追加"
```

### Task 8.3: ProductSettingsSheet に ImageField を配線

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/ProductSettingsSheet.kt`
- Modify: ProductSettings の ViewModel / 呼び出し元(onUploadImage / onRemoveImage を流す)

- [ ] **Step 1: ProductSettingsSheet にコールバックと ImageField を追加**

シグネチャに追加:
```kotlin
image: ImageBitmap?,
onPickImage: () -> Unit,
onRemoveImage: () -> Unit,
```
商品名セクション(line 89)直後に:
```kotlin
AppText(stringResource(Res.string.image_field_label_section), /* 600/12 faint */) // "画像"
Spacer(Modifier.height(10.dp))
ImageField(image = image, fallbackIcon = glyphForProductName(stock.product.name()), onPick = onPickImage, onRemove = onRemoveImage)
Spacer(Modifier.height(22.dp))
```
(`image_field_label_section` = "画像" を strings に追加)

- [ ] **Step 2: 呼び出し元で picker → uploadImage → 画像再ロードを配線**

ProductSettings を開いている画面(ProductMaster 経由)で:
```kotlin
val scope = rememberCoroutineScope()
val loader = LocalProductImageLoader.current
ProductSettingsSheet(
    // ...
    image = rememberProductImage(loader, stock.product.id(), stock.product.image is ProductImage.Stored),
    onPickImage = {
        scope.launch {
            val base64 = pickImageAsBase64() ?: return@launch
            when (repository.uploadImage(stock.product.id(), base64)) {
                is RpcOutcome.Success -> { /* refresh 経路で商品再取得 → image=Stored に。loader キャッシュは productId 単位なので無効化が要れば clear */ }
                is RpcOutcome.Failure -> { /* toast */ }
            }
        }
    },
    onRemoveImage = {
        scope.launch { repository.changeImage(stock.product.id(), ProductImage.None) /* 既存 */ }
    },
)
```
> アップロード成功後、`ProductImageLoader` のキャッシュを当該 productId 分だけ無効化する `invalidate(productId)` を Loader に足し、再ロードさせる(新しい ref で presigned URL が変わるため)。

- [ ] **Step 3: ProductImageLoader に invalidate を追加**

```kotlin
suspend fun invalidate(productId: ProductId) = mutex.withLock { cache.remove(productId) }
```

- [ ] **Step 4: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: dev server で E2E 目視(backend + Garage 起動下)**

`./gradlew :frontend:jsBrowserDevelopmentRun --continuous` で、商品設定シートを開き「画像を追加」→ ファイル選択 → アップロード → シート/一覧の Thumb に反映、を確認。mock(`screens-master.jsx:ImageField`)と見た目を突き合わせ。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/ProductSettingsSheet.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/image/ProductImageLoader.kt
git commit -m "feat(frontend): 商品設定に画像欄(ピッカー→アップロード→反映)を配線"
```

---

# Stage 9: 仕上げ

### Task 9.1: フル build と全テスト

- [ ] **Step 1: backend 全テスト(非 integration)**

Run: `./gradlew :backend:core:test :backend:api:test :domain:jvmTest :rpc:jvmTest`
Expected: 全 PASS

- [ ] **Step 2: integration テスト(Garage + Postgres 起動下)**

```bash
docker compose up -d postgres zitadel garage && docker compose up zitadel-init garage-init
set -a; . ./.env.zitadel; . ./.env.garage; set +a
# TEST_DB_URL / TEST_STORAGE_* を export
./gradlew integrationTest
```
Expected: 全 PASS(画像 E2E 含む)。

- [ ] **Step 3: frontend コンパイル(js + wasmJs)**

Run: `./gradlew :frontend:compileKotlinJs :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL(wasmJs は重いので OOM 注意。必要なら js のみ)。

- [ ] **Step 4: 忠実度の最終目視**

dev server で ImageField / Thumb 画像表示を mock と突き合わせ、`docs/superpowers/fidelity/` に倣ってプロパティ適合を確認。必要なら微修正。

### Task 9.2: spec / メモリ更新と PR

- [ ] **Step 1: spec のステータス更新(実装完了を追記)**

- [ ] **Step 2: PR 作成**

```bash
git push -u origin feat/p6-4b-product-image
gh pr create --title "feat: 商品画像(Garage保存 + presigned表示 + Wasmピッカー)" --body "..."
```
> コミットメッセージ/PR body に issue/PR 番号を書かない(プロジェクト規約)。

---

## Self-Review(計画作成者チェック済)

- **Spec coverage**: §2(Garage/upload backend/presigned)→Stage0,3,5,7。§3(ストレージ/CORS)→Task0.2,0.3,3.2。§4(処理)→Task2.1。§5(domain VO)→Stage1。§6(application)→Stage4。§7(RPC)→Stage5。§8(frontend Thumb/ImageField/picker)→Stage7,8。§9(テスト)→各 Stage + Stage9。§10(実装順)→Stage 構成が一致。§11(割り切り)→単純同期/期限/CORS を各所に反映。
- **未解決の実装時確認(注記済・各 Task に明記)**: ① aws-sdk-kotlin の `CredentialsProvider`/`presignGetObject`/`forcePathStyle` の正確な API(版差)② garage CLI v2.3.0 のサブコマンドフラグ ③ Garage バケット CORS の設定経路(CLI か S3 PutBucketCors か)④ `decodeToImageBitmap` の import パス ⑤ frontend の AppButton/AppText/service アクセサの既存 API 名。いずれもコンパイル/実行で即検出でき、計画の骨子は不変。
- **型整合**: `RawImageUpload`/`ImageUrl`/`ProductImageStorageRepository(store/presignedUrl)`/`UploadImageRequest(base64)`/`uploadImage`/`imageUrl` を全 Stage で一貫使用。`StoredImage`/`ImageMediaType` は不使用で統一。
