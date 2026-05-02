#!/bin/bash
# 1) 创建 org_config 表（机构级配置）
# 2) 顺手删除测试用的 checkin_record id=1，让你重新打卡
# 3) 重建后端 + 机构端
cd "$(dirname "$0")"

echo "==========================================="
echo " 1/4 创建 org_config 表"
echo "==========================================="
# 注意：docker exec 必须加 -i 才能从 stdin 读取 heredoc 内容
docker exec -i jy_postgres psql -U postgres -d jy_safety <<'SQL'
CREATE TABLE IF NOT EXISTS org_config (
    id           BIGSERIAL PRIMARY KEY,
    org_id       BIGINT       NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    config_key   VARCHAR(128) NOT NULL,
    config_value VARCHAR(255) NOT NULL,
    description  VARCHAR(255),
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (org_id, config_key)
);
CREATE INDEX IF NOT EXISTS idx_org_config_org   ON org_config(org_id);
CREATE INDEX IF NOT EXISTS idx_org_config_key   ON org_config(config_key);
\d org_config
SQL
echo "✓ 上方应该看到 8 列的 org_config 表结构。如果什么都没显示，建表失败了！"

echo ""
echo "==========================================="
echo " 2/4 删除堵路的测试打卡记录"
echo "==========================================="
docker exec jy_postgres psql -U postgres -d jy_safety -c \
  "DELETE FROM checkin_record WHERE id = 1; SELECT COUNT(*) AS remaining FROM checkin_record;"

echo ""
echo "==========================================="
echo " 3/4 重建后端 + 机构端"
echo "==========================================="
docker compose build backend org

echo ""
echo "==========================================="
echo " 4/4 重启容器 + 清缓存"
echo "==========================================="
docker compose up -d backend org
sleep 25
docker exec jy_redis redis-cli -a jy_redis_2026 FLUSHDB 2>/dev/null
echo "✓ 缓存已清空"

echo ""
echo "==========================================="
echo " 验证"
echo "==========================================="
echo "测试上传接口（应该返回 401，因为没带 token）："
curl -s -o /dev/null -w "  /api/v1/checkin/upload-photo → %{http_code}\n" \
     -X POST http://localhost:8080/api/v1/checkin/upload-photo

echo ""
echo "测试机构配置接口（应该返回 401）："
curl -s -o /dev/null -w "  /api/v1/org/checkin-config → %{http_code}\n" \
     http://localhost:8080/api/v1/org/checkin-config

echo ""
echo "==========================================="
echo " 完成！"
echo "==========================================="
echo "→ 机构端 http://localhost:81 → 左侧菜单底部「机构设置」→「打卡规则」"
echo "  3 个字段都可配；留空 = 沿用平台默认；填值 = 本机构生效"
echo ""
echo "→ 小程序点编译，重新打卡走完拍照上传流程，应能在机构端审核页看到带照片的记录"
echo ""
echo "→ 如果你想改平台默认（影响所有未单独配置的机构）："
echo "   docker exec jy_postgres psql -U postgres -d jy_safety -c \\"
echo "     \"UPDATE sys_config SET config_value='10' WHERE config_key='max_checkins_per_period';\""
