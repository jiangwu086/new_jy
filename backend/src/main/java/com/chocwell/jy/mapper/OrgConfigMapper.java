package com.chocwell.jy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chocwell.jy.entity.OrgConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrgConfigMapper extends BaseMapper<OrgConfig> {

    /**
     * 取指定机构的某个配置值；未配置返回 null。
     */
    @Select("SELECT config_value FROM org_config WHERE org_id = #{orgId} AND config_key = #{key}")
    String getValue(@Param("orgId") Long orgId, @Param("key") String key);
}
