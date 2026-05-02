#!/bin/bash
# 修复点：
#   1) RedisConfig 换成 GenericJackson2JsonRedisSerializer，解决 List 缓存反序列化失败
#   2) frontend-org 加了 leaflet 依赖 + LocationList.vue 地图选点
#   3) frontend-admin 之前已经改过，可以一并重建
cd "$(dirname "$0")"

echo "==========================================="
echo " 1/4 重建后端镜像"
echo "==========================================="
docker compose build backend

echo ""
echo "==========================================="
echo " 2/4 重建管理后台 + 机构端"
echo "==========================================="
docker compose build admin org

echo ""
echo "==========================================="
echo " 3/4 重启所有相关容器"
echo "==========================================="
docker compose up -d backend admin org

echo "等待后端启动..."
sleep 25

echo ""
echo "【缓存】清空 Redis（去掉旧格式遗留数据）..."
docker exec jy_redis redis-cli -a jy_redis_2026 FLUSHDB 2>/dev/null
echo "✓ 缓存已清空"

echo ""
echo "==========================================="
echo " 4/4 验证：连续两次请求 /api/v1/locations"
echo "==========================================="

call_locs() {
  local label="$1"
  local resp=$(curl -s --max-time 10 "http://localhost:8080/api/v1/locations")
  local code=$(echo "$resp" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code','?'))" 2>/dev/null)
  local cnt=$(echo  "$resp" | python3 -c "import sys,json;print(len(json.load(sys.stdin).get('data') or []))" 2>/dev/null)
  echo "  $label → code=$code, 点位数=$cnt"
}

# 第一次 = 走 DB + 写缓存
call_locs "首次请求(DB)        "
# 第二次 = 走 Redis 缓存（关键测试点）
call_locs "二次请求(Redis 缓存)"
# 第三次 = 再走一次缓存
call_locs "三次请求(Redis 缓存)"

echo ""
echo "→ 三次都返回 code=200 且点位数一致 = ✅ Redis 反序列化已修复"
echo "→ 微信开发者工具点「编译」刷新小程序，首页应能看到所有打卡点"
echo "→ 浏览器访问 http://localhost:81 (机构端) 进打卡点位管理 → 新增，应见地图选点"
echo "→ 浏览器访问 http://localhost     (平台端) 同样可用"
