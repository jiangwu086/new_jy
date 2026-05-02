-- 机构端功能 v2 升级迁移脚本
-- 在已有数据库上执行此脚本即可，幂等安全（IF NOT EXISTS / IF NOT EXISTS column）

-- 1. 新建机构表
CREATE TABLE IF NOT EXISTS organization (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) UNIQUE NOT NULL,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    address VARCHAR(255),
    logo_url VARCHAR(512),
    status SMALLINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. sys_admin 增加 org_id 列
ALTER TABLE sys_admin ADD COLUMN IF NOT EXISTS org_id BIGINT REFERENCES organization(id);

-- 3. edu_location 增加 org_id 列
ALTER TABLE edu_location ADD COLUMN IF NOT EXISTS org_id BIGINT REFERENCES organization(id);
CREATE INDEX IF NOT EXISTS idx_edu_location_org ON edu_location(org_id);

-- 4. 礼品目录表
CREATE TABLE IF NOT EXISTS gift_item (
    id BIGSERIAL PRIMARY KEY,
    org_id BIGINT REFERENCES organization(id),
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    image_url VARCHAR(512),
    points_cost INT NOT NULL,
    stock INT DEFAULT -1,
    sort_order INT DEFAULT 0,
    status SMALLINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gift_item_org ON gift_item(org_id, status);

-- 5. redeem_record 增加礼品关联
ALTER TABLE redeem_record ADD COLUMN IF NOT EXISTS gift_id BIGINT REFERENCES gift_item(id);
ALTER TABLE redeem_record ADD COLUMN IF NOT EXISTS gift_name VARCHAR(128);

-- 6. sys_user 中 created_at 别名（某些查询用 created_at，实际字段为 create_time）
--    如果你的 sys_user 表字段是 create_time 而不是 created_at，此步骤不需要。
--    仅当字段名真的是 created_at 时执行：
-- ALTER TABLE sys_user RENAME COLUMN created_at TO create_time;
