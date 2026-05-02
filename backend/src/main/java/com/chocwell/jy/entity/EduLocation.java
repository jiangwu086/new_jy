package com.chocwell.jy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("edu_location")
public class EduLocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 点位名称 */
    private String name;

    /** 详细地址 */
    private String address;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 状态：0-关闭，1-营业中（通过 status 字段做软删除，无 deleted 列） */
    private Integer status;

    /** 所属机构ID（null 表示平台直营） */
    private Long orgId;

    /** 所属区划代码 */
    private String districtCode;

    /** 点位专属打卡二维码 URL */
    private String qrCodeUrl;

    /** 打卡半径限制（米），默认 200 */
    private Integer checkinRadius;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
