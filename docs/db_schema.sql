-- 新就业形态劳动者安全警示服务系统 PostgreSQL 初始化脚本

-- 1. 用户表 (sys_user)
-- 包含骑手的基本信息及总积分。手机号和身份证建议在应用层加密后存入。
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    openid VARCHAR(64) UNIQUE,                -- 微信开放ID（手机号登录用户可为空）
    unionid VARCHAR(64),                      -- 微信UnionID（跨应用唯一标识）
    phone VARCHAR(255) UNIQUE,               -- 手机号 (建议应用层AES加密后存储)
    nick_name VARCHAR(128),                   -- 微信昵称
    avatar_url VARCHAR(512),                  -- 微信头像URL
    id_card VARCHAR(255),                     -- 身份证号 (建议应用层AES加密后存储)
    real_name VARCHAR(128),                   -- 真实姓名
    verify_status SMALLINT DEFAULT 0,         -- 实名认证状态：0-未提交 1-审核中 2-已通过 3-已拒绝
    verify_reject_reason VARCHAR(255),        -- 审核拒绝原因
    total_points INT DEFAULT 0,               -- 累计可用积分
    deleted SMALLINT DEFAULT 0,              -- 逻辑删除：0-正常，1-已删除
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- 已有数据库平滑迁移（幂等）
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS verify_status SMALLINT DEFAULT 0;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS verify_reject_reason VARCHAR(255);
CREATE INDEX idx_sys_user_phone ON sys_user(phone);

-- 2. 教育点位表 (edu_location)
-- 存储线下的打卡点位信息
CREATE TABLE IF NOT EXISTS edu_location (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,               -- 点位名称
    address VARCHAR(255),                     -- 详细地址
    longitude NUMERIC(10, 6) NOT NULL,        -- 经度 (后续可升级为PostGIS Geometry)
    latitude NUMERIC(10, 6) NOT NULL,         -- 纬度 (后续可升级为PostGIS Geometry)
    status SMALLINT DEFAULT 1,                -- 状态：0-关闭，1-营业中
    district_code VARCHAR(32),                -- 所属区划代码 (如济南市历下区)
    qr_code_url VARCHAR(512),                 -- 点位专属打卡二维码
    checkin_radius INT DEFAULT 200,           -- 打卡有效半径（米）
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_edu_location_district ON edu_location(district_code);

-- 3. 打卡记录表 (checkin_record)
-- 记录骑手的每一次打卡行为，是系统的核心业务表
CREATE TABLE IF NOT EXISTS checkin_record (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,                  -- 关联 sys_user
    location_id BIGINT NOT NULL,              -- 关联 edu_location
    photo_url VARCHAR(512) NOT NULL,          -- 现场带水印合照OSS地址
    gps_longitude NUMERIC(10, 6) NOT NULL,    -- 提交打卡时的实际经度
    gps_latitude NUMERIC(10, 6) NOT NULL,     -- 提交打卡时的实际纬度
    checkin_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- 打卡时间
    period VARCHAR(32) NOT NULL,              -- 所属周期，例如 '2026-04'或'2026-Q2'
    points_earned INT DEFAULT 0,              -- 本次打卡获得的积分
    status SMALLINT DEFAULT 1,                -- 状态：0-无效/异常，1-有效
    review_status SMALLINT DEFAULT 1,         -- 人工审核状态：0-待审核，1-审核通过，2-审核驳回
    review_remark VARCHAR(255),               -- 驳回原因

    CONSTRAINT fk_checkin_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_checkin_location FOREIGN KEY (location_id) REFERENCES edu_location(id)
);
-- 联合索引：用于快速统计用户在一个周期内的有效打卡次数
CREATE INDEX idx_checkin_user_period ON checkin_record(user_id, period, status);
-- 唯一部分索引：同一用户同一周期在同一点位只能有效打卡一次（防并发重复）
-- WHERE status=1 确保无效/驳回记录不影响重打
CREATE UNIQUE INDEX IF NOT EXISTS idx_checkin_unique_user_period_loc
    ON checkin_record(user_id, period, location_id)
    WHERE status = 1;

-- 4. 积分变动明细表 (points_log)
-- 记录积分的获取与核销流水
CREATE TABLE IF NOT EXISTS points_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type SMALLINT NOT NULL,                   -- 类型：1-打卡获得, 2-线下核销扣减, 3-系统调整, 4-在线兑换礼品
    amount INT NOT NULL,                      -- 变动额度 (正负数)
    remark VARCHAR(255),                      -- 备注
    related_id BIGINT,                        -- 关联业务ID (打卡ID或核销ID)
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_points_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
CREATE INDEX idx_points_log_user ON points_log(user_id);

-- 5. 积分核销记录表 (redeem_record)
-- 记录线下点位兑换物品的核销动作
CREATE TABLE IF NOT EXISTS redeem_record (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    location_id BIGINT,                       -- 在哪个点位核销（线上兑换时为 null）
    points_used INT NOT NULL,                 -- 消耗积分数
    operator_id BIGINT,                       -- 操作核销的工作人员/管理员ID
    gift_id BIGINT,                           -- 关联礼品ID（线上兑换时有值）
    gift_name VARCHAR(128),                   -- 礼品名称冗余
    remark VARCHAR(255),                      -- 备注
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_redeem_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_redeem_location FOREIGN KEY (location_id) REFERENCES edu_location(id)
);

-- 6. 内容文章表 (article)
-- 用于展示政策、理赔指南及区域安全案例
CREATE TABLE IF NOT EXISTS article (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT,                             -- 富文本内容
    district_code VARCHAR(32),                -- 绑定特定区域展示 (为空则全域展示)
    type SMALLINT NOT NULL,                   -- 1-政策说明, 2-理赔指南, 3-警示案例
    sort_order INT DEFAULT 0,                 -- 排序权重（值越大越靠前）
    cover_url VARCHAR(512),                   -- 封面图 URL
    status SMALLINT DEFAULT 1,                -- 0-隐藏，1-发布
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_article_district_type ON article(district_code, type);

-- 7. 系统配置表 (sys_config)
-- 动态管理周期规则等
CREATE TABLE IF NOT EXISTS sys_config (
    id SERIAL PRIMARY KEY,
    config_key VARCHAR(128) UNIQUE NOT NULL,  -- 如: max_checkin_per_month, points_per_checkin
    config_value VARCHAR(255) NOT NULL,       -- 配置值
    description VARCHAR(255),                 -- 描述
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初始化基础配置
INSERT INTO sys_config (config_key, config_value, description) VALUES
('points_per_checkin', '10', '单次有效打卡获得的积分数'),
('max_checkins_per_period', '5', '每个周期单人打卡上限次数'),
('current_period_type', 'MONTH', '当前计费周期类型(MONTH/QUARTER)');

-- 8. 管理员表 (sys_admin)
-- 后台管理系统账号，不同于普通用户
CREATE TABLE IF NOT EXISTS sys_admin (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,     -- 登录用户名
    password_hash VARCHAR(255) NOT NULL,      -- BCrypt 加密后的密码
    real_name VARCHAR(64),                    -- 真实姓名
    role VARCHAR(32) DEFAULT 'OPERATOR',      -- 角色：SUPER_ADMIN-超管, ADMIN-管理员, OPERATOR-操作员
    district_code VARCHAR(32),                -- 负责的区域（为空表示管全区域）
    status SMALLINT DEFAULT 1,                -- 0-禁用，1-启用
    last_login_time TIMESTAMP,                -- 最后登录时间
    deleted SMALLINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初始超级管理员（密码: Admin@123456，BCrypt hash，生产环境务必修改）
INSERT INTO sys_admin (username, password_hash, real_name, role) VALUES
('admin', '$2b$10$8IAnvVqvL1RJ5l5E0.bLSePQmP1dsmMLFMvr6IPnG4YFNXo4jfGSO', '超级管理员', 'SUPER_ADMIN')
ON CONFLICT (username) DO NOTHING;

-- ─────────────────────────────────────────────────────────────
-- 机构端扩展（v2）
-- ─────────────────────────────────────────────────────────────

-- 9. 机构表 (organization)
-- 平台入驻机构（外卖平台、劳务派遣公司等）
-- 必须在 gift_item 之前创建，gift_item.org_id 引用此表
CREATE TABLE IF NOT EXISTS organization (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,                -- 机构名称
    code VARCHAR(64) UNIQUE NOT NULL,          -- 机构编号（唯一）
    contact_name VARCHAR(64),                  -- 联系人姓名
    contact_phone VARCHAR(32),                 -- 联系电话
    address VARCHAR(255),                      -- 机构地址
    logo_url VARCHAR(512),                     -- Logo URL
    status SMALLINT DEFAULT 1,                 -- 0-禁用，1-启用
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 10. sys_admin 增加机构归属 & 角色调整
-- role: SUPER_ADMIN-超管, ADMIN-平台管理员, ORG_ADMIN-机构管理员
ALTER TABLE sys_admin ADD COLUMN IF NOT EXISTS org_id BIGINT REFERENCES organization(id);

-- 11. edu_location 增加机构归属
ALTER TABLE edu_location ADD COLUMN IF NOT EXISTS org_id BIGINT REFERENCES organization(id);
CREATE INDEX IF NOT EXISTS idx_edu_location_org ON edu_location(org_id);

-- ─────────────────────────────────────────────────────────────
-- 礼品体系（v2）
-- ─────────────────────────────────────────────────────────────

-- 礼品目录表 (gift_item)
-- org_id 为 NULL 表示平台通用礼品，非 NULL 表示机构专属礼品
-- organization 表已在上方创建，FK 可正常引用
CREATE TABLE IF NOT EXISTS gift_item (
    id BIGSERIAL PRIMARY KEY,
    org_id BIGINT REFERENCES organization(id),  -- null=平台礼品，非null=机构专属
    name VARCHAR(128) NOT NULL,                  -- 礼品名称
    description VARCHAR(512),                    -- 描述
    image_url VARCHAR(512),                      -- 图片URL
    points_cost INT NOT NULL,                    -- 兑换所需积分
    stock INT DEFAULT -1,                        -- 库存(-1=不限库存)
    sort_order INT DEFAULT 0,                    -- 排序（越大越前）
    status SMALLINT DEFAULT 1,                   -- 0-下架，1-上架
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gift_item_org ON gift_item(org_id, status);

-- redeem_record 增加 gift_id 关联
ALTER TABLE redeem_record ADD COLUMN IF NOT EXISTS gift_id BIGINT REFERENCES gift_item(id);
ALTER TABLE redeem_record ADD COLUMN IF NOT EXISTS gift_name VARCHAR(128); -- 冗余名称防止礼品被删后丢失信息

-- 示例机构数据（可选）
-- INSERT INTO organization(name,code,contact_name,contact_phone) VALUES ('美团骑士济南分公司','MT-JN-001','张经理','13800138001');
-- INSERT INTO sys_admin(username,password_hash,real_name,role,org_id) VALUES ('org_admin1','$2b$10$8IAnvVqvL1RJ5l5E0.bLSePQmP1dsmMLFMvr6IPnG4YFNXo4jfGSO','机构管理员','ORG_ADMIN',1);

-- ── 2026 迁移 ──────────────────────────────────────────────────────────────

-- edu_location 经纬度改为可空（管理员可先填地址后填坐标，或用地图选点补全）
ALTER TABLE edu_location
    ALTER COLUMN longitude DROP NOT NULL,
    ALTER COLUMN latitude  DROP NOT NULL;
