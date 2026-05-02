package com.chocwell.jy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chocwell.jy.entity.EduLocation;
import com.chocwell.jy.mapper.LocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationMapper locationMapper;

    /**
     * 获取营业中的点位列表（小程序端使用）
     * 缓存 key：locations::{districtCode}（districtCode 为空时 key = locations::_all）
     * TTL 10 分钟（见 RedisConfig）
     */
    @Cacheable(value = "locations", key = "#districtCode != null ? #districtCode : '_all'")
    public List<EduLocation> listActive(String districtCode) {
        LambdaQueryWrapper<EduLocation> wrapper = new LambdaQueryWrapper<EduLocation>()
                .eq(EduLocation::getStatus, 1)
                .orderByAsc(EduLocation::getId);
        if (districtCode != null && !districtCode.isBlank()) {
            wrapper.eq(EduLocation::getDistrictCode, districtCode);
        }
        return locationMapper.selectList(wrapper);
    }

    /**
     * 获取全部点位（管理后台使用）
     */
    @Cacheable(value = "locations", key = "'admin_all'")
    public List<EduLocation> listAll() {
        return locationMapper.selectList(
                new LambdaQueryWrapper<EduLocation>().orderByDesc(EduLocation::getCreateTime));
    }

    public EduLocation getById(Long id) {
        return locationMapper.selectById(id);
    }

    /** 保存后清除 locations 缓存 */
    @CacheEvict(value = "locations", allEntries = true)
    public void save(EduLocation location) {
        if (location.getId() == null) {
            locationMapper.insert(location);
        } else {
            locationMapper.updateById(location);
        }
    }

    /** 关闭点位后清除 locations 缓存 */
    @CacheEvict(value = "locations", allEntries = true)
    public void delete(Long id) {
        EduLocation loc = new EduLocation();
        loc.setId(id);
        loc.setStatus(0); // 逻辑关闭而非物理删除
        locationMapper.updateById(loc);
    }

    /** 营业中点位总数 */
    public int countActive() {
        return Math.toIntExact(locationMapper.selectCount(
                new LambdaQueryWrapper<EduLocation>().eq(EduLocation::getStatus, 1)));
    }

    /** 指定机构的点位列表 */
    public List<EduLocation> listByOrg(Long orgId) {
        return locationMapper.selectList(
                new LambdaQueryWrapper<EduLocation>()
                        .eq(EduLocation::getOrgId, orgId)
                        .orderByDesc(EduLocation::getCreateTime));
    }

    /** 指定机构的点位 ID 列表 */
    public List<Long> listIdsByOrg(Long orgId) {
        return listByOrg(orgId).stream().map(EduLocation::getId).toList();
    }
}
