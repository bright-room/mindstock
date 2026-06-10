#!/bin/sh
# Garage をローカル用に宣言的セットアップする(compose up 時に garage-init が実行)。
# - layout(1 ノード・容量割当)を確定
# - bucket `mindstock-images`
# - **固定 dev アクセスキー**を import(read/write/owner を bucket に付与)
# 冪等(再実行で既存を再利用)。dev 用の固定資格情報なので postgres の mindstock/mindstock や
# garage.toml の rpc_secret/admin_token と同じく平文で OK。本番は環境変数 STORAGE_ACCESS_KEY /
# STORAGE_SECRET_KEY が application.yaml のデフォルトを上書きする。
# 固定キーにしたことで `.env.garage` の生成・sourcing は不要(app/test は application.yaml の
# デフォルトとして同じ値を持つ)。
#
# dxflrs/garage は scratch イメージで shell を持たないため、本スクリプトは alpine で
# 動かし garage の admin API(:3903)を curl 越しに叩く(zitadel-init と同じ方式)。
set -eu

ADMIN="http://garage:3903"
TOKEN="localadmintoken"
BUCKET="mindstock-images"
KEY="mindstock-key"
# 固定 dev キー(application.yaml の external.storage デフォルトと一致させること)。
# Garage の形式: access key = "GK"+24hex、secret = 64hex。
FIXED_AK="GKdeadbeefdeadbeefdeadbeef"
FIXED_SK="deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"

echo "[garage-init] installing curl/jq ..."
apk add --no-cache curl jq >/dev/null

# $1=METHOD $2=PATH(?query 含む) $3=BODY(任意)
api() {
  if [ "$#" -ge 3 ]; then
    curl -fsS -X "$1" "$ADMIN$2" -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" -d "$3"
  else
    curl -fsS -X "$1" "$ADMIN$2" -H "Authorization: Bearer $TOKEN"
  fi
}

echo "[garage-init] waiting for garage admin API ..."
i=0
until api GET /v2/GetClusterStatus >/dev/null 2>&1; do
  i=$((i + 1)); [ "$i" -gt 60 ] && { echo "[garage-init] garage not ready, abort"; exit 1; }
  sleep 2
done

# --- layout ---
# 既にロールが割り当て済みなら何もしない(冪等)。未割り当てなら staged role を
# 投入して次バージョンへ apply する。capacity はバイト指定(1GB)。
NODE_ID="$(api GET /v2/GetClusterStatus | jq -r '.nodes[0].id')"
ROLE="$(api GET /v2/GetClusterLayout | jq -r --arg id "$NODE_ID" '.roles[] | select(.id==$id) | .id')"
if [ -z "$ROLE" ]; then
  CUR_VER="$(api GET /v2/GetClusterLayout | jq -r '.version')"
  NEXT_VER="$((CUR_VER + 1))"
  api POST /v2/UpdateClusterLayout \
    "{\"roles\":[{\"id\":\"$NODE_ID\",\"zone\":\"dc1\",\"capacity\":1000000000,\"tags\":[]}]}" >/dev/null
  api POST /v2/ApplyClusterLayout "{\"version\":$NEXT_VER}" >/dev/null
  echo "[garage-init] assigned layout (v$NEXT_VER) to $NODE_ID"
else
  echo "[garage-init] reuse layout for $NODE_ID"
fi

# --- bucket ---
BID="$(api GET "/v2/GetBucketInfo?globalAlias=$BUCKET" 2>/dev/null | jq -r '.id // empty')"
if [ -z "$BID" ]; then
  BID="$(api POST /v2/CreateBucket "{\"globalAlias\":\"$BUCKET\"}" | jq -r '.id')"
  echo "[garage-init] created bucket $BUCKET = $BID"
else
  echo "[garage-init] reuse bucket $BUCKET = $BID"
fi

# --- access key(固定 dev キーを import・冪等) ---
# GetKeyInfo が 404(未存在)なら ImportKey で固定キーを投入。既存ならそのまま使う。
if api GET "/v2/GetKeyInfo?id=$FIXED_AK" >/dev/null 2>&1; then
  echo "[garage-init] reuse fixed key $FIXED_AK"
else
  api POST /v2/ImportKey \
    "{\"accessKeyId\":\"$FIXED_AK\",\"secretAccessKey\":\"$FIXED_SK\",\"name\":\"$KEY\"}" >/dev/null
  echo "[garage-init] imported fixed key $FIXED_AK"
fi

# --- bucket への権限付与(冪等: 何度叩いても同じ状態に収束) ---
api POST /v2/AllowBucketKey \
  "{\"bucketId\":\"$BID\",\"accessKeyId\":\"$FIXED_AK\",\"permissions\":{\"read\":true,\"write\":true,\"owner\":true}}" >/dev/null

echo "[garage-init] bucket=$BUCKET key=$FIXED_AK ready (creds は application.yaml デフォルトと一致)"
echo "[garage-init] done."
