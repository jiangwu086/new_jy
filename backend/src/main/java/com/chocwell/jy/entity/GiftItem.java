package com.chocwell.jy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gift_item")
public class GiftItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属机构ID，null = 平台通用礼品 */
    private Long orgId;

    /** 礼品名称 */
    private String name;

    /** 礼品描述 */
    private String description;

    /** 图片 URL */
    private String imageUrl;

    /** 兑换所需积分 */
    private Integer pointsCost;

    /** 库存，-1 表示不限 */
    private Integer stock;

    /** 排序权重（越大越靠前） */
    private Integer sortOrder;

    /** 状态：0-下架，1-上架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
