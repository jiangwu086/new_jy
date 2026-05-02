package com.chocwell.jy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("redeem_record")
public class RedeemRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 在哪个点位核销 */
    private Long locationId;

    /** 消耗积分数 */
    private Integer pointsUsed;

    /** 操作核销的管理员ID */
    private Long operatorId;

    /** 备注（兑换了什么物品等） */
    private String remark;

    /** 关联礼品ID（可选，线上兑换时有值） */
    private Long giftId;

    /** 礼品名称冗余（防止礼品被删后数据丢失） */
    private String giftName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
