-- ============================================================
-- 2026 迁移脚本（对已有运行中的数据库执行）
-- 执行方式：
--   docker exec jy_postgres psql -U postgres -d jy_safety -f /migrate_2026.sql
-- 或将此文件复制进容器再执行
-- ============================================================

-- 1. edu_location 经纬度允许为空
--    （之前 NOT NULL 导致未填坐标时报"系统繁忙"）
ALTER TABLE edu_location
    ALTER COLUMN longitude DROP NOT NULL,
    ALTER COLUMN latitude  DROP NOT NULL;

-- 2. 确认修改结果
SELECT column_name, is_nullable, data_type
FROM information_schema.columns
WHERE table_name = 'edu_location'
  AND column_name IN ('longitude', 'latitude');
