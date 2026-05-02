package com.chocwell.jy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chocwell.jy.entity.EduLocation;
import com.chocwell.jy.entity.Organization;
import com.chocwell.jy.entity.SysAdmin;
import com.chocwell.jy.mapper.*;
import com.chocwell.jy.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrgService {

    private final AdminMapper    adminMapper;
    private final OrgMapper      orgMapper;
    private final LocationMapper locationMapper;
    private final CheckinMapper  checkinMapper;
    private final UserMapper     userMapper;
    private final JwtUtil        jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ─── 登录 ────────────────────────────────────────────────

    /**
     * 机构管理员登录
     * 账号角色必须为 ORG_ADMIN，且有归属 orgId
     */
    public Map<String, Object> login(String username, String password) {
        SysAdmin admin = adminMapper.findByUsername(username);
        if (admin == null || admin.getStatus() == 0) {
            throw new IllegalArgumentException("账号不存在或已禁用");
        }
        if (!"ORG_ADMIN".equals(admin.getRole())) {
            throw new IllegalArgumentException("非机构管理员账号，请使用平台管理后台登录");
        }
        if (admin.getOrgId() == null) {
            throw new IllegalArgumentException("账号未关联机构，请联系平台管理员");
        }
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new IllegalArgumentException("密码错误");
        }

        // 更新登录时间
        SysAdmin upd = new SysAdmin();
        upd.setId(admin.getId());
        upd.setLastLoginTime(LocalDateTime.now());
        adminMapper.updateById(upd);

        Organization org = orgMapper.selectById(admin.getOrgId());

        String token = jwtUtil.generateOrg(admin.getId(), admin.getOrgId());

        Map<String, Object> data = new HashMap<>();
        data.put("token",    token);
        data.put("adminId",  admin.getId());
        data.put("realName", admin.getRealName());
        data.put("orgId",    admin.getOrgId());
        data.put("orgName",  org != null ? org.getName() : "");
        return data;
    }

    // ─── 机构信息 ────────────────────────────────────────────

    public Organization getOrg(Long orgId) {
        return orgMapper.selectById(orgId);
    }

    // ─── 点位管理 ────────────────────────────────────────────

    public List<EduLocation> listLocations(Long orgId) {
        return locationMapper.selectList(
                new LambdaQueryWrapper<EduLocation>()
                        .eq(EduLocation::getOrgId, orgId)
                        .orderByDesc(EduLocation::getCreateTime));
    }

    public List<Long> locationIds(Long orgId) {
        return listLocations(orgId).stream().map(EduLocation::getId).toList();
    }

    /** OrgController 中使用的别名，与 locationIds() 等价 */
    public List<Long> listIdsByOrg(Long orgId) {
        return locationIds(orgId);
    }

    /** 保存点位后清空 locations 缓存，确保小程序立即看到最新数据 */
    @CacheEvict(value = "locations", allEntries = true)
    public EduLocation saveLocation(Long orgId, EduLocation loc) {
        loc.setOrgId(orgId);  // 强制绑定到当前机构
        if (loc.getId() == null) {
            locationMapper.insert(loc);
        } else {
            // 安全校验：只能修改自己机构的点位
            EduLocation exist = locationMapper.selectById(loc.getId());
            if (exist == null || !orgId.equals(exist.getOrgId())) {
                throw new IllegalArgumentException("无权操作此点位");
            }
            // 用全量 update 而非 selective，避免空字符串/null 字段被忽略导致前端看不到更新
            locationMapper.updateById(loc);
        }
        return loc;
    }

    /** 关闭点位后清空 locations 缓存 */
    @CacheEvict(value = "locations", allEntries = true)
    public void deleteLocation(Long orgId, Long locationId) {
        EduLocation exist = locationMapper.selectById(locationId);
        if (exist == null || !orgId.equals(exist.getOrgId())) {
            throw new IllegalArgumentException("无权操作此点位");
        }
        EduLocation upd = new EduLocation();
        upd.setId(locationId);
        upd.setStatus(0);
        locationMapper.updateById(upd);
    }

    // ─── 数据大屏 ────────────────────────────────────────────

    public Map<String, Object> dashboard(Long orgId) {
        List<Long> ids = locationIds(orgId);
        Map<String, Object> data = new HashMap<>();
        if (ids.isEmpty()) {
            data.put("totalLocations", 0);
            data.put("totalWorkers",   0);
            data.put("todayCheckins",  0);
            data.put("monthCheckins",  0);
            data.put("recentCheckins", List.of());
            return data;
        }
        data.put("totalLocations", ids.size());
        data.put("totalWorkers",   userMapper.countWorkersByLocations(ids));
        data.put("todayCheckins",  checkinMapper.countTodayByLocations(ids));
        data.put("monthCheckins",  checkinMapper.countMonthByLocations(ids));
        data.put("recentCheckins", checkinMapper.recentCheckinsByLocations(ids, 10));
        return data;
    }

    // ─── 打卡记录 ────────────────────────────────────────────

    public Map<String, Object> pageCheckins(Long orgId, int page, int size,
                                             Long locationId, String period, Integer status) {
        List<Long> ids = locationIds(orgId);
        if (ids.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("list", List.of()); empty.put("total", 0);
            return empty;
        }
        // 安全校验：locationId 必须属于此机构
        Long safeLocationId = null;
        if (locationId != null && ids.contains(locationId)) {
            safeLocationId = locationId;
        } else if (locationId != null) {
            // 不属于此机构，返回空
            Map<String, Object> empty = new HashMap<>();
            empty.put("list", List.of()); empty.put("total", 0);
            return empty;
        }
        int offset = (page - 1) * size;
        var list  = checkinMapper.pageAdminList(null, safeLocationId, period, status, size, offset);
        long total = checkinMapper.countAdminList(null, safeLocationId, period, status);

        // 进一步过滤：只保留属于本机构点位的记录（当 locationId=null 时需额外过滤）
        if (safeLocationId == null) {
            final List<Long> finalIds = ids;
            list = list.stream()
                    .filter(r -> {
                        Object lid = r.get("location_id");
                        return lid != null && finalIds.contains(((Number)lid).longValue());
                    })
                    .toList();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    // ─── 员工列表 ────────────────────────────────────────────

    /**
     * 在本机构点位打过卡的不重复员工
     */
    public List<Map<String, Object>> listWorkers(Long orgId) {
        List<Long> ids = locationIds(orgId);
        if (ids.isEmpty()) return List.of();
        return userMapper.selectMaps(
                new LambdaQueryWrapper<com.chocwell.jy.entity.SysUser>()
                        .select(com.chocwell.jy.entity.SysUser::getId,
                                com.chocwell.jy.entity.SysUser::getNickName,
                                com.chocwell.jy.entity.SysUser::getPhone,
                                com.chocwell.jy.entity.SysUser::getRealName,
                                com.chocwell.jy.entity.SysUser::getTotalPoints)
                        .inSql(com.chocwell.jy.entity.SysUser::getId,
                                "SELECT DISTINCT user_id FROM checkin_record WHERE status=1 AND location_id IN ("
                                + String.join(",", ids.stream().map(String::valueOf).toList()) + ")")
                        .orderByDesc(com.chocwell.jy.entity.SysUser::getTotalPoints));
    }
}
