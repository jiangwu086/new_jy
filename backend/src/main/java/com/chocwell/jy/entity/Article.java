package com.chocwell.jy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文章标题 */
    private String title;

    /** 封面图 URL */
    private String coverUrl;

    /** 富文本内容 */
    private String content;

    /** 绑定区域展示（为空则全域可见） */
    private String districtCode;

    /**
     * 类型：1-政策说明, 2-理赔指南, 3-警示案例
     */
    private Integer type;

    /** 排序权重（值越大越靠前） */
    private Integer sortOrder;

    /** 状态：0-隐藏，1-发布 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
