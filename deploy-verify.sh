#!/bin/bash
# 实名审核功能部署脚本
set -e

cd "$(dirname "$0")"

echo "=== 1. 数据库迁移 ==="
docker exec jy_postgres psql -U postgres jy_safety -c "
  -- 实名认证字段（幂等）
  ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS verify_status SMALLINT DEFAULT 0;
  ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS verify_reject_reason VARCHAR(255);

  -- 打卡审核字段（幂等）— checkin_record 原建表未包含这两列
  ALTER TABLE checkin_record ADD COLUMN IF NOT EXISTS review_status SMALLINT DEFAULT 1;
  ALTER TABLE checkin_record ADD COLUMN IF NOT EXISTS review_remark VARCHAR(255);

  -- 礼品在线兑换修复：redeem_record.location_id 改为可空（线上兑换无点位）
  ALTER TABLE redeem_record ALTER COLUMN location_id DROP NOT NULL;

  -- 防重复打卡：唯一部分索引（幂等）
  CREATE UNIQUE INDEX IF NOT EXISTS idx_checkin_unique_user_period_loc
      ON checkin_record(user_id, period, location_id)
      WHERE status = 1;
"
echo "数据库迁移完成"

echo ""
echo "=== 2. 重新构建镜像 ==="
docker compose build backend admin

echo ""
echo "=== 3. 重启容器 ==="
docker compose up -d backend admin

echo ""
echo "=== 部署完成 ==="
echo "访问管理后台，侧边栏应出现「实名审核」菜单项"
