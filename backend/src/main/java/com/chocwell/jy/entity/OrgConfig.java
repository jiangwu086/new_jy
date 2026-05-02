package com.chocwell.jy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 机构级配置 — 覆盖 sys_config 同名 key 的全局默认值。
 * 单表唯一索引 (org_id, config_key)。
 */
@Data
@TableName("org_config")
public class OrgConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属机构 ID */
    private Long orgId;

    /** 配置键，如 points_per_checkin / max_checkins_per_period / current_period_type */
    private String configKey;

    /** 配置值（字符串存储） */
    private String configValue;

    /** 描述 */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
