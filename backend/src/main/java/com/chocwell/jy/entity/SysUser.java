package com.chocwell.jy.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 微信 openid（小程序唯一标识） */
    private String openid;

    /** 微信 unionid（已绑定开放平台时有值） */
    private String unionid;

    /** 手机号（建议 AES 加密存储） */
    private String phone;

    /** 身份证号（建议 AES 加密存储） */
    private String idCard;

    /** 真实姓名 */
    private String realName;

    /**
     * 实名认证状态：0-未提交 1-审核中 2-已通过 3-已拒绝
     */
    private Integer verifyStatus;

    /** 审核拒绝原因（status=3 时填写） */
    private String verifyRejectReason;

    /** 微信昵称（用户自行设置后回填） */
    private String nickName;

    /** 微信头像 URL */
    private String avatarUrl;

    /** 累计积分 */
    private Integer totalPoints;

    /**
     * 逻辑删除：0-正常，1-已删除
     * 与 application.yml 中 logic-delete-field: deleted 配套使用
     */
    @TableLogic
    @TableField(select = false)   // API 序列化时不输出此字段
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
