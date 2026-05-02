#!/bin/bash
# 修复 JacksonConfig 导致的 LocalDateTime 序列化失败
cd "$(dirname "$0")"

echo "【重建】后端镜像（修复 JacksonConfig）..."
docker compose build --no-cache backend

echo "【重启】容器..."
docker compose up -d

echo "等待后端启动（30 秒）..."
sleep 30

echo "【缓存】清空 Redis..."
docker exec jy_redis redis-cli -a jy_redis_2026 FLUSHDB 2>/dev/null
echo "✓ 缓存已清空"

echo ""
echo "接口验证..."
sleep 3

ARTICLE_RESP=$(curl -s --max-time 10 "http://localhost:8080/api/v1/articles")
ARTICLE_CODE=$(echo "$ARTICLE_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','?'))" 2>/dev/null)
ARTICLE_CNT=$(echo "$ARTICLE_RESP"  | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data') or []))" 2>/dev/null)
ARTICLE_MSG=$(echo "$ARTICLE_RESP"  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('msg',''))" 2>/dev/null)

GIFT_RESP=$(curl -s --max-time 10 "http://localhost:8080/api/v1/gifts")
GIFT_CODE=$(echo "$GIFT_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','?'))" 2>/dev/null)
GIFT_CNT=$(echo "$GIFT_RESP"  | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data') or []))" 2>/dev/null)

LOC_RESP=$(curl -s --max-time 10 "http://localhost:8080/api/v1/locations")
LOC_CODE=$(echo "$LOC_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','?'))" 2>/dev/null)
LOC_CNT=$(echo "$LOC_RESP"  | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data') or []))" 2>/dev/null)

echo "  /api/v1/articles  → code=$ARTICLE_CODE, 文章数=$ARTICLE_CNT ${ARTICLE_MSG:+(msg: $ARTICLE_MSG)}"
echo "  /api/v1/gifts     → code=$GIFT_CODE,    礼品数=$GIFT_CNT"
echo "  /api/v1/locations → code=$LOC_CODE,     点位数=$LOC_CNT"

if [ "$ARTICLE_CODE" = "200" ] && [ "$GIFT_CODE" = "200" ]; then
  echo ""
  echo "✅ 接口全部正常！"
  echo "→ 注意：文章的 district_code 是 '123'，小程序不传 districtCode 时查不到这条。"
  echo "  请在管理后台把文章的「区划代码」清空（留空 = 全域可见），或新建一篇不填区划的文章。"
  echo ""
  echo "→ 微信开发者工具点「编译」刷新小程序，就能看到数据了。"
else
  echo ""
  echo "❌ 仍有问题，后端最新日志："
  docker logs jy_backend --tail=20 2>&1
fi
