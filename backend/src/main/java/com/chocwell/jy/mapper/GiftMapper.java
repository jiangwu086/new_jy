package com.chocwell.jy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chocwell.jy.entity.GiftItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GiftMapper extends BaseMapper<GiftItem> {

    /**
     * 原子扣减库存（库存 > 0 或库存 = -1 时才更新）
     * 返回受影响行数，0 表示库存不足
     */
    @Update("UPDATE gift_item SET stock = stock - 1 " +
            "WHERE id = #{id} AND status = 1 AND (stock = -1 OR stock > 0)")
    int decreaseStock(@Param("id") Long id);
}
