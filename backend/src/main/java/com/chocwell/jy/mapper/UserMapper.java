package com.chocwell.jy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chocwell.jy.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE openid = #{openid} AND deleted = 0 LIMIT 1")
    SysUser findByOpenid(@Param("openid") String openid);

    @Select("SELECT * FROM sys_user WHERE phone = #{phone} AND deleted = 0 LIMIT 1")
    SysUser findByPhone(@Param("phone") String phone);

    /** 原子性积分累加（避免先查再写的并发问题） */
    @Update("UPDATE sys_user SET total_points = total_points + #{delta} WHERE id = #{userId}")
    void addPoints(@Param("userId") Long userId, @Param("delta") int delta);

    /** 用户总数 */
    @Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0")
    int countAll();

    /** 今日新增用户数 */
    @Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND DATE(create_time) = CURRENT_DATE")
    int countTodayNew();

    /** 机构端：在指定点位打过卡的不重复用户数 */
    @Select("<script>" +
            "SELECT COUNT(DISTINCT cr.user_id) FROM checkin_record cr " +
            "WHERE cr.status=1 AND cr.location_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int countWorkersByLocations(@Param("ids") List<Long> locationIds);
}
