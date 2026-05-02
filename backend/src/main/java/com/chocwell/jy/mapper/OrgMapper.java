package com.chocwell.jy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chocwell.jy.entity.Organization;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrgMapper extends BaseMapper<Organization> {
}
