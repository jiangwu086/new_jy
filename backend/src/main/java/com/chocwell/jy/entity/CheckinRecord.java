package com.chocwell.jy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("checkin_record")
public class CheckinRecord {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private Long locationId;
    
    private String photoUrl;
    
    private BigDecimal gpsLongitude;
    
    private BigDecimal gpsLatitude;
    
    private LocalDateTime checkinTime;
    
    /**
     * 所属周期，如 2026-04
     */
    private String period;
    
    private Integer pointsEarned;
    
    /**
     * 状态：0-异常/无效，1-有效
     */
    private Integer status;

    /**
     * 审核状态：0-待审核，1-审核通过，2-审核驳回
     * 默认为 1（自动通过），开启人工审核后新记录为 0
     */
    private Integer reviewStatus;

    /** 驳回原因 */
    private String reviewRemark;
}
