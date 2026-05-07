-- ════════════════════════════════════════════════════════════════════════
-- v4 迁移：补建 org_config 表（机构级配置覆盖 sys_config 同名 key）
-- ════════════════════════════════════════════════════════════════════════
-- 背景：
--   OrgConfigService / OrgConfigMapper 已经写好，但 db_schema.sql 和
--   前几个 migrate_*.sql 都漏了 org_config 表本身。线上机构端打开
--   "打卡规则" 页 (GET /api/v1/org/checkin-config) 会因表不存在
--   抛 SQLException → 前端拿到"系统繁忙，请稍后再试"。
--
-- 影响：纯 DDL，幂等(IF NOT EXISTS)，不会动现有数据。
-- ════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS org_config (
    id            BIGSERIAL    PRIMARY KEY,
    org_id        BIGINT       NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    config_key    VARCHAR(128) NOT NULL,                          -- 如 points_per_checkin
    config_value  VARCHAR(255),                                   -- 字符串存储，空 = 回退全局
    description   VARCHAR(255),
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_org_config_key UNIQUE (org_id, config_key)
);

-- update_time 自动维护触发器（可选，MyBatis-Plus 已经在 entity 上做了 INSERT_UPDATE 填充，
-- 但加一个 DB 层兜底，防止直接 SQL 修改时漏更新）
CREATE OR REPLACE FUNCTION trg_org_config_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS org_config_update_time_trg ON org_config;
CREATE TRIGGER org_config_update_time_trg
BEFORE UPDATE ON org_config
FOR EACH ROW EXECUTE FUNCTION trg_org_config_update_time();

COMMENT ON TABLE  org_config              IS '机构级配置（覆盖 sys_config 同名 key）';
COMMENT ON COLUMN org_config.config_key   IS 'key，与 sys_config.config_key 同语义';
COMMENT ON COLUMN org_config.config_value IS '值，空字符串/NULL 视为不覆盖、回退全局';
