#!/bin/bash
# 1) 后端：礼品查询全部公开（移除 org_id 过滤）
# 2) 管理后台：打卡点表单加 Leaflet 地图选点
cd "$(dirname "$0")"

echo "==========================================="
echo " 1/4 重建后端镜像（GiftService 改动）"
echo "==========================================="
docker compose build backend

echo ""
echo "==========================================="
echo " 2/4 重建管理后台镜像（Vue 安装 leaflet）"
echo "==========================================="
docker compose build admin

echo ""
echo "==========================================="
echo " 3/4 重启 backend / admin 容器"
echo "==========================================="
docker compose up -d backend admin

echo "等待后端启动..."
sleep 25

echo ""
echo "【缓存】清空 Redis..."
docker exec jy_redis redis-cli -a jy_redis_2026 FLUSHDB 2>/dev/null
echo "✓ 缓存已清空"

echo ""
echo "==========================================="
echo " 4/4 验证礼品接口"
echo "==========================================="
GIFT_RESP=$(curl -s --max-time 10 "http://localhost:8080/api/v1/gifts")
GIFT_CODE=$(echo "$GIFT_RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code','?'))" 2>/dev/null)
GIFT_CNT=$(echo  "$GIFT_RESP" | python3 -c "import sys,json;print(len(json.load(sys.stdin).get('data') or []))" 2>/dev/null)
echo "  /api/v1/gifts → code=$GIFT_CODE, 礼品数=$GIFT_CNT"

# 临时插一条机构礼品验证：如果接口返回 ≥2 条说明机构礼品也能查到
echo ""
echo "【验证】临时插一条机构礼品..."
docker exec jy_postgres psql -U postgres -d jy_safety -c \
  "INSERT INTO gift_item (name, points_cost, stock, status, org_id, sort_order, create_time, update_time)
   VALUES ('【验证用】机构礼品', 100, 5, 1, 1, 0, NOW(), NOW())
   ON CONFLICT DO NOTHING RETURNING id, name, org_id;" 2>&1 | head -5

# 清缓存重新查
docker exec jy_redis redis-cli -a jy_redis_2026 FLUSHDB 2>/dev/null > /dev/null
GIFT_RESP2=$(curl -s --max-time 10 "http://localhost:8080/api/v1/gifts")
GIFT_CNT2=$(echo "$GIFT_RESP2" | python3 -c "import sys,json;print(len(json.load(sys.stdin).get('data') or []))" 2>/dev/null)
echo "  插入机构礼品后 /api/v1/gifts → 礼品数=$GIFT_CNT2"

if [ "$GIFT_CNT2" -ge 2 ] 2>/dev/null; then
  echo "  ✅ 机构礼品也能查到了！"
else
  echo "  ⚠️  机构礼品没显示，请检查后端日志：docker logs jy_backend --tail=30"
fi

echo ""
echo "==========================================="
echo " 完成！"
echo "==========================================="
echo "→ 小程序点编译，礼品页应该能看到「【验证用】机构礼品」"
echo "→ 浏览器打开 http://localhost 进入管理后台"
echo "  在「打卡点位管理」点新增/编辑，下方应显示地图"
echo "  在地图上点击即可填经纬度，标记可拖动微调"
echo ""
echo "→ 验证完想删除测试礼品："
echo "   docker exec jy_postgres psql -U postgres -d jy_safety -c \\"
echo "     \"DELETE FROM gift_item WHERE name='【验证用】机构礼品';\""
