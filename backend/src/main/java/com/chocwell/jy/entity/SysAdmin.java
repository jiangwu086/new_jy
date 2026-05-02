package com.chocwell.jy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_admin")
public class SysAdmin {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名 */
    private String username;

    /** BCrypt 加密后的密码 */
    private String passwordHash;

    /** 真实姓名 */
    private String realName;

    /**
     * 角色：SUPER_ADMIN-超级管理员, ADMIN-管理员, OPERATOR-操作员, ORG_ADMIN-机构管理员
     */
    private String role;

    /** 所属机构ID（为 null 表示平台管理员；非 null 表示机构管理员） */
    private Long orgId;

    /** 负责的区划代码（为空表示管全区域） */
    private String districtCode;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    @TableLogic
    @TableField(value = "deleted")
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
