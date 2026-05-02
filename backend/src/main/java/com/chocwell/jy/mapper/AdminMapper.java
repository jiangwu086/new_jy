package com.chocwell.jy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chocwell.jy.entity.SysAdmin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminMapper extends BaseMapper<SysAdmin> {

    @Select("SELECT * FROM sys_admin WHERE username = #{username} AND deleted = 0 LIMIT 1")
    SysAdmin findByUsername(@Param("username") String username);
}
