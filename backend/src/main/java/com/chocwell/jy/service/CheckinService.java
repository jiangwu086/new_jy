package com.chocwell.jy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chocwell.jy.entity.CheckinRecord;
import com.chocwell.jy.entity.EduLocation;
import com.chocwell.jy.entity.PointsLog;
import com.chocwell.jy.entity.SysUser;
import com.chocwell.jy.mapper.CheckinMapper;
import com.chocwell.jy.mapper.LocationMapper;
import com.chocwell.jy.mapper.PointsLogMapper;
import com.chocwell.jy.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private final CheckinMapper checkinMapper;
    private final LocationMapper locationMapper;
    private final UserMapper userMapper;
    private final PointsLogMapper pointsLogMapper;
    private final ConfigService configService;
    private final OrgConfigService orgConfigService;   // 机构级配置（覆盖 sys_config）
    private final StringRedisTemplate redisTemplate;
    private final WxService wxService;

    // Redis 分布式锁前缀，有效期 30 秒防重复提交
    private static final String LOCK_PREFIX = "checkin:lock:";
    private static final long LOCK_TTL_SEC = 30;

    /**
     * 打卡初始化校验：
     * 1. 点位是否存在且营业
     * 2. 本周期是否已达上限
     * 返回 Map: { locationName, currentCount, maxCount, canCheckin }
     */
    public Map<String, Object> initCheck(Long userId, Long locationId) {
        EduLocation location = locationMapper.selectById(locationId);
        if (location == null || location.getStatus() == 0) {
            throw new IllegalArgumentException("打卡点位不存在或已关闭");
        }

        Long orgId = location.getOrgId();   // 平台公共点位 orgId 为 null，自动走全局
        int maxCount = orgConfigService.getInt(orgId, "max_checkins_per_period", 5);
        String period = currentPeriod(orgId);
        int currentCount = checkinMapper.countByUserPeriod(userId, period);

        Map<String, Object> result = new HashMap<>();
        result.put("locationId", location.getId());
        result.put("locationName", location.getName());
        result.put("currentCount", currentCount);
        result.put("maxCount", maxCount);
        result.put("canCheckin", currentCount < maxCount);
        return result;
    }

    /**
     * 提交打卡
     * 1. Redis 分布式锁防并发刷单
     * 2. 距离校验（Haversine 公式）
     * 3. 写 checkin_record，更新积分，写 points_log
     * 4. 异步发送订阅消息（不影响主流程）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(Long userId, Long locationId,
                                      double userLng, double userLat,
                                      String photoUrl) {
        String lockKey = LOCK_PREFIX + userId + ":" + locationId;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL_SEC, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            throw new IllegalStateException("请勿重复提交，稍后再试");
        }

        try {
            EduLocation location = locationMapper.selectById(locationId);
            if (location == null || location.getStatus() == 0) {
                throw new IllegalArgumentException("打卡点位不存在或已关闭");
            }

            // 距离校验（默认 200 米半径）
            int radius = location.getCheckinRadius() != null ? location.getCheckinRadius() : 200;
            double dist = haversineMeters(
                    userLat, userLng,
                    location.getLatitude().doubleValue(),
                    location.getLongitude().doubleValue());
            if (dist > radius) {
                throw new IllegalArgumentException(
                        String.format("距离打卡点太远（%.0f 米），请靠近后再试", dist));
            }

            // 周期上限校验（按机构级配置 → 全局默认）
            Long orgId = location.getOrgId();   // 平台公共点位 orgId 为 null，走全局
            int maxCount = orgConfigService.getInt(orgId, "max_checkins_per_period", 5);
            String period = currentPeriod(orgId);
            int currentCount = checkinMapper.countByUserPeriod(userId, period);
            if (currentCount >= maxCount) {
                throw new IllegalStateException("本周期打卡次数已达上限（" + maxCount + " 次）");
            }

            // 读取用户 openId（用于订阅消息推送）
            SysUser user = userMapper.selectById(userId);

            // 写打卡记录（唯一索引兜底：若并发导致重复，抛友好错误而非 500）
            int points = orgConfigService.getInt(orgId, "points_per_checkin", 10);
            LocalDateTime checkinTime = LocalDateTime.now();
            CheckinRecord record = new CheckinRecord();
            record.setUserId(userId);
            record.setLocationId(locationId);
            record.setPhotoUrl(photoUrl);
            record.setGpsLongitude(BigDecimal.valueOf(userLng));
            record.setGpsLatitude(BigDecimal.valueOf(userLat));
            record.setCheckinTime(checkinTime);
            record.setPeriod(period);
            record.setPointsEarned(points);
            record.setStatus(1);
            record.setReviewStatus(0);   // 0 = 待审核（管理员可选择性事后复核）
            try {
                checkinMapper.insert(record);
            } catch (DataIntegrityViolationException e) {
                // 唯一索引触发：同周期该点位已有有效打卡
                throw new IllegalStateException("本周期您已在该点位打卡，请前往其他教育点");
            }

            // 更新用户总积分
            userMapper.addPoints(userId, points);

            // 写积分流水
            PointsLog pointsLog = new PointsLog();
            pointsLog.setUserId(userId);
            pointsLog.setType(1); // 打卡获得
            pointsLog.setAmount(points);
            pointsLog.setRelatedId(record.getId());
            pointsLog.setRemark("打卡获得积分 — " + location.getName());
            pointsLogMapper.insert(pointsLog);

            // 异步推送订阅消息（事务提交后执行，失败不回滚）
            final String openid      = user != null ? user.getOpenid() : null;
            final String locName     = location.getName();
            final int    finalPoints = points;
            final String timeStr     = checkinTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            CompletableFuture.runAsync(() ->
                wxService.notifyCheckin(openid, locName, finalPoints, timeStr)
            );

            Map<String, Object> result = new HashMap<>();
            result.put("checkinId", record.getId());
            result.put("pointsEarned", points);
            result.put("currentCount", currentCount + 1);
            result.put("maxCount", maxCount);
            return result;

        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 管理后台分页打卡记录
     */
    public Map<String, Object> pageAdmin(int page, int size,
                                         Long userId, Long locationId,
                                         String period, Integer status) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = checkinMapper.pageAdminList(
                userId, locationId, period, status, size, offset);
        long total = checkinMapper.countAdminList(userId, locationId, period, status);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 用户打卡历史（最近 50 条，附带点位名称/地址）
     */
    public List<Map<String, Object>> history(Long userId) {
        return checkinMapper.userHistory(userId, 50);
    }

    // ─── 工具方法 ─────────────────────────────────────────────

    /**
     * 当前周期字符串，按系统配置 MONTH → "2026-04"，QUARTER → "2026-Q2"
     */
    /** 旧签名保留向后兼容（走全局配置） */
    private String currentPeriod() {
        return currentPeriod(null);
    }

    /**
     * 计算当前打卡周期；orgId != null 时按该机构的 current_period_type 取，
     * 否则使用全局 sys_config 中的值。
     */
    private String currentPeriod(Long orgId) {
        String type = orgConfigService.get(orgId, "current_period_type");
        LocalDateTime now = LocalDateTime.now();
        if ("QUARTER".equalsIgnoreCase(type)) {
            int q = (now.getMonthValue() - 1) / 3 + 1;
            return now.getYear() + "-Q" + q;
        }
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    /**
     * Haversine 公式计算两点间距离（米）
     */
    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000; // 地球半径（米）
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
