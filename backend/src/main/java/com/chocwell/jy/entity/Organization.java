package com.chocwell.jy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("organization")
public class Organization {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 机构名称 */
    private String name;

    /** 机构唯一编号 */
    private String code;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 机构地址 */
    private String address;

    /** Logo URL */
    private String logoUrl;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
