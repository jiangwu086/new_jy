package com.chocwell.jy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chocwell.jy.entity.EduLocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LocationMapper extends BaseMapper<EduLocation> {
    /** 大屏地图点位 */
    @Select("SELECT id, name, address, longitude, latitude, district_code, status " +
            "FROM edu_location WHERE status = 1 ORDER BY id")
    List<Map<String, Object>> activeLocationMap();
}
