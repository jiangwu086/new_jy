#!/bin/bash
# ============================================================
# fix_and_rebuild.sh
# 一键修复「用户端看不到数据」问题
#   1. 运行数据库迁移（longitude/latitude 改为可空）
#   2. 重新构建后端 + 管理后台镜像（含代码修复）
#   3. 重启所有容器
#   4. 清除 Redis 缓存（防止旧空列表缓存）
#   5. 验证接口返回是否正常
# ============================================================

set -e
cd "$(dirname "$0")"

echo ""
echo "=========================================="
echo "  JY Safety — 一键修复部署脚本"
echo "=========================================="
echo ""

# ── Step 1: 数据库迁移 ──────────────────────────────────────
echo "【1/5】运行数据库迁移..."
if docker exec jy_postgres psql -U postgres -d jy_safety \
     -c "SELECT column_name, is_nullable FROM information_schema.columns \
         WHERE table_name='edu_location' AND column_name='longitude';" \
   2>/dev/null | grep -q "YES"; then
  echo "  ✓ 迁移已执行过，跳过"
else
  docker cp docs/migrate_2026.sql jy_postgres:/migrate_2026.sql
  docker exec jy_postgres psql -U postgres -d jy_safety -f /migrate_2026.sql
  echo "  ✓ 迁移完成"
fi

# ── Step 2 & 3: 重建镜像并重启容器 ─────────────────────────
echo ""
echo "【2/5】重新构建后端镜像（含代码修复）..."
docker compose build --no-cache backend

echo ""
echo "【3/5】重新构建管理后台镜像..."
docker compose build --no-cache admin

echo ""
echo "【4/5】重启所有容器..."
docker compose up -d

echo ""
echo "  等待后端健康检查（最多 60 秒）..."
for i in $(seq 1 12); do
  sleep 5
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' jy_backend 2>/dev/null || echo "unknown")
  echo "  → 第 ${i} 次检查，状态: $STATUS"
  if [ "$STATUS" = "healthy" ]; then
    echo "  ✓ 后端已就绪"
    break
  fi
done

# ── Step 4: 清除 Redis 缓存 ──────────────────────────────────
echo ""
echo "【5/5】清除 Redis 缓存（防止旧空列表缓存干扰）..."
docker exec jy_redis redis-cli -a jy_redis_2026 FLUSHDB 2>/dev/null || \
docker exec jy_redis redis-cli -a jy_redis_2026 KEYS '*' | \
  xargs -r docker exec -i jy_redis redis-cli -a jy_redis_2026 DEL
echo "  ✓ 缓存已清空"

# ── Step 5: 验证接口 ─────────────────────────────────────────
echo ""
echo "=========================================="
echo "  验证接口数据..."
echo "=========================================="

echo ""
echo "▶ 数据库数据量："
docker exec jy_postgres psql -U postgres -d jy_safety -c "
SELECT '文章(article)' AS 表名,
       count(*) AS 总数,
       count(*) FILTER (WHERE status=1) AS 已发布
FROM article
UNION ALL
SELECT '礼品(gift_item)',
       count(*),
       count(*) FILTER (WHERE status=1)
FROM gift_item
UNION ALL
SELECT '点位(edu_location)',
       count(*),
       count(*) FILTER (WHERE status=1)
FROM edu_location;" 2>/dev/null

echo ""
echo "▶ 接口响应（直接调 localhost:8080）："

ARTICLE_RESP=$(curl -s --max-time 5 "http://localhost:8080/api/v1/articles" 2>/dev/null)
ARTICLE_CODE=$(echo "$ARTICLE_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','?'))" 2>/dev/null)
ARTICLE_CNT=$(echo "$ARTICLE_RESP"  | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data') or []))" 2>/dev/null)
echo "  /api/v1/articles → code=$ARTICLE_CODE, 文章数=$ARTICLE_CNT"

GIFT_RESP=$(curl -s --max-time 5 "http://localhost:8080/api/v1/gifts" 2>/dev/null)
GIFT_CODE=$(echo "$GIFT_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','?'))" 2>/dev/null)
GIFT_CNT=$(echo "$GIFT_RESP"  | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data') or []))" 2>/dev/null)
echo "  /api/v1/gifts    → code=$GIFT_CODE, 礼品数=$GIFT_CNT"

LOC_RESP=$(curl -s --max-time 5 "http://localhost:8080/api/v1/locations" 2>/dev/null)
LOC_CODE=$(echo "$LOC_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','?'))" 2>/dev/null)
LOC_CNT=$(echo "$LOC_RESP"  | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data') or []))" 2>/dev/null)
echo "  /api/v1/locations → code=$LOC_CODE, 点位数=$LOC_CNT"

echo ""
echo "=========================================="
echo "  全部完成！"
echo ""
echo "  如果上面接口返回的数量 > 0，"
echo "  重新编译小程序 → 微信开发者工具里点「编译」即可看到数据。"
echo ""
echo "  如果数量还是 0，请把上方输出截图给我看。"
echo "=========================================="
