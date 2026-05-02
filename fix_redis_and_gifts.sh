#!/bin/bash
# ============================================================
# fix_redis_and_gifts.sh
# 修复两个已知问题：
#   1. 文章接口返回 code=400（Redis LocalDateTime 序列化冲突）
#   2. 礼品接口返回 0 条（诊断 orgId 问题）
# ============================================================

set -e
cd "$(dirname "$0")"

echo ""
echo "=========================================="
echo "  诊断 + 修复"
echo "=========================================="

# ── 诊断：查看礼品 orgId ─────────────────────────────────────
echo ""
echo "【诊断】礼品表数据详情："
docker exec jy_postgres psql -U postgres -d jy_safety -c "
SELECT id, name, status, org_id,
       CASE WHEN org_id IS NULL THEN '✓ 平台礼品（用户可见）'
            ELSE '✗ 机构礼品（仅机构用户可见，orgId=' || org_id || '）'
       END AS 可见性
FROM gift_item
ORDER BY id;" 2>/dev/null

echo ""
echo "【诊断】文章表数据详情："
docker exec jy_postgres psql -U postgres -d jy_safety -c "
SELECT id, title, type, status, district_code,
       CASE WHEN district_code IS NULL OR district_code='' THEN '✓ 全域可见'
            ELSE '区划限制: ' || district_code
       END AS 可见性
FROM article
ORDER BY id;" 2>/dev/null

# ── 询问是否把机构礼品改为平台礼品 ──────────────────────────
echo ""
echo "如果上面显示礼品是「机构礼品」，自动将 org_id 改为 NULL（平台通用）..."
GIFT_ORG_COUNT=$(docker exec jy_postgres psql -U postgres -d jy_safety -tAc \
  "SELECT count(*) FROM gift_item WHERE org_id IS NOT NULL AND status=1" 2>/dev/null | tr -d ' ')

if [ "$GIFT_ORG_COUNT" -gt "0" ] 2>/dev/null; then
  echo "  → 发现 $GIFT_ORG_COUNT 个机构礼品，将 org_id 置为 NULL（改为平台礼品）..."
  docker exec jy_postgres psql -U postgres -d jy_safety -c \
    "UPDATE gift_item SET org_id = NULL WHERE org_id IS NOT NULL AND status=1;"
  echo "  ✓ 已修复礼品可见性"
else
  echo "  ✓ 所有上架礼品已是平台礼品，无需修复"
fi

# ── 重建后端（含 RedisConfig 修复）─────────────────────────
echo ""
echo "【重建】后端镜像（修复 Redis LocalDateTime 序列化问题）..."
docker compose build --no-cache backend

echo ""
echo "【重启】容器..."
docker compose up -d

echo ""
echo "  等待后端启动（30 秒）..."
sleep 30

# ── 清除 Redis 缓存 ──────────────────────────────────────────
echo ""
echo "【缓存】清空 Redis..."
docker exec jy_redis redis-cli -a jy_redis_2026 FLUSHDB 2>/dev/null
echo "  ✓ 缓存已清空"

# ── 验证 ─────────────────────────────────────────────────────
echo ""
echo "=========================================="
echo "  接口验证..."
echo "=========================================="
sleep 5

ARTICLE_RESP=$(curl -s --max-time 10 "http://localhost:8080/api/v1/articles" 2>/dev/null)
ARTICLE_CODE=$(echo "$ARTICLE_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','?'))" 2>/dev/null)
ARTICLE_CNT=$(echo "$ARTICLE_RESP"  | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data') or []))" 2>/dev/null)
ARTICLE_MSG=$(echo "$ARTICLE_RESP"  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('msg',''))" 2>/dev/null)

GIFT_RESP=$(curl -s --max-time 10 "http://localhost:8080/api/v1/gifts" 2>/dev/null)
GIFT_CODE=$(echo "$GIFT_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','?'))" 2>/dev/null)
GIFT_CNT=$(echo "$GIFT_RESP"  | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data') or []))" 2>/dev/null)

echo ""
echo "  /api/v1/articles → code=$ARTICLE_CODE, 文章数=$ARTICLE_CNT ${ARTICLE_MSG:+(msg: $ARTICLE_MSG)}"
echo "  /api/v1/gifts    → code=$GIFT_CODE,    礼品数=$GIFT_CNT"

echo ""
if [ "$ARTICLE_CODE" = "200" ] && [ "$ARTICLE_CNT" -gt "0" ] 2>/dev/null && \
   [ "$GIFT_CODE" = "200" ] && [ "$GIFT_CNT" -gt "0" ] 2>/dev/null; then
  echo "  ✅ 全部正常！"
  echo "  → 微信开发者工具点「编译」，用户端就能看到文章和礼品了。"
elif [ "$ARTICLE_CODE" = "200" ] 2>/dev/null; then
  echo "  ⚠️  文章 OK，但礼品还是 0 条。"
  echo "  → 请去管理后台重新创建一个礼品，归属选「平台通用」（不选机构）。"
else
  echo "  ❌ 仍有问题，请把上面的输出发给我。"
  echo ""
  echo "  后端日志（最后 30 行）："
  docker logs jy_backend --tail=30 2>&1
fi
echo "=========================================="
