package com.chocwell.jy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("points_log")
public class PointsLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 类型：1-打卡获得, 2-线下核销扣减, 3-系统调整, 4-在线兑换礼品
     */
    private Integer type;

    /** 变动额度（正数为增加，负数为扣减） */
    private Integer amount;

    /** 备注 */
    private String remark;

    /** 关联业务ID（打卡记录ID 或 核销记录ID） */
    private Long relatedId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
