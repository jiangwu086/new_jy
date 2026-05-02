#!/bin/bash
# 修复点：
#   1) 打卡记录列表显示用户姓名（后端 SQL 加 real_name + 前端按 realName→nickName→phone 优先级）
#   2) CheckinService.submit 加 setReviewStatus(0)，新打卡进待审核列表
#   3) DB 历史 review_status NULL → 0，让旧记录也能在审核页看到
cd "$(dirname "$0")"

echo "==========================================="
echo " 1/5 数据库：批量修正 review_status NULL → 0"
echo "==========================================="
docker exec jy_postgres psql -U postgres -d jy_safety -c \
  "UPDATE checkin_record SET review_status = 0 WHERE review_status IS NULL;
   SELECT COUNT(*) AS pending_count FROM checkin_record WHERE review_status = 0;"

echo ""
echo "==========================================="
echo " 2/5 重建后端镜像（CheckinService + Mapper SQL 改动）"
echo "==========================================="
docker compose build backend

echo ""
echo "==========================================="
echo " 3/5 重建 admin + org（前端列模板改动）"
echo "==========================================="
docker compose build admin org

echo ""
echo "==========================================="
echo " 4/5 重启容器 + 清缓存"
echo "==========================================="
docker compose up -d backend admin org
sleep 25
docker exec jy_redis redis-cli -a jy_redis_2026 FLUSHDB 2>/dev/null
echo "✓ 缓存已清空"

echo ""
echo "==========================================="
echo " 5/5 数据库验证"
echo "==========================================="
echo ""
echo "【sys_user 当前数据】"
docker exec jy_postgres psql -U postgres -d jy_safety -c \
  "SELECT id, real_name, nick_name, phone, verify_status FROM sys_user LIMIT 10;"

echo "【checkin_record review_status 分布】"
docker exec jy_postgres psql -U postgres -d jy_safety -c \
  "SELECT review_status, COUNT(*) FROM checkin_record GROUP BY review_status ORDER BY review_status;"

echo ""
echo "==========================================="
echo " 完成！"
echo "==========================================="
echo "→ 机构端 http://localhost:81 → 打卡记录：用户姓名按【真实姓名→昵称→手机号】顺序显示"
echo "→ 机构端 http://localhost:81 → 打卡审核：能看到 review_status=0 的待审核记录"
echo "→ 平台端 http://localhost    → 打卡审核：同样可见"
echo ""
echo "→ 没看到姓名通常是因为这个用户没做实名认证，real_name 为空、nick_name 也是微信默认昵称"
echo "  可以先去管理后台「实名认证」让用户走完认证流程，real_name 就有了"
