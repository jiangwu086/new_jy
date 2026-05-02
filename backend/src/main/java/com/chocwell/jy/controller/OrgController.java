package com.chocwell.jy.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.excel.annotation.ExcelProperty;
import com.chocwell.jy.entity.CheckinRecord;
import com.chocwell.jy.entity.EduLocation;
import com.chocwell.jy.entity.GiftItem;
import com.chocwell.jy.interceptor.OrgAuthInterceptor;
import com.chocwell.jy.mapper.CheckinMapper;
import com.chocwell.jy.mapper.PointsLogMapper;
import com.chocwell.jy.mapper.UserMapper;
import com.chocwell.jy.service.GiftService;
import com.chocwell.jy.service.OrgConfigService;
import com.chocwell.jy.service.OrgService;
import com.chocwell.jy.util.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 机构端 API（统一前缀 /api/v1/org）
 * 除 /login 外均需机构管理员 token（OrgAuthInterceptor 处理）
 */
@RestController
@RequestMapping("/api/v1/org")
@RequiredArgsConstructor
public class OrgController {

    private final OrgService        orgService;
    private final GiftService       giftService;
    private final OrgConfigService  orgConfigService;
    private final CheckinMapper     checkinMapper;
    private final UserMapper        userMapper;
    private final PointsLogMapper   pointsLogMapper;

    // ─── 登录 ────────────────────────────────────────────────

    /**
     * POST /api/v1/org/login
     * body: { username, password }
     */
    @PostMapping("/login")
    public R<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) return R.fail("用户名和密码不能为空");
        try {
            return R.ok(orgService.login(username, password));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    // ─── 机构信息 ────────────────────────────────────────────

    /** GET /api/v1/org/info */
    @GetMapping("/info")
    public R<?> info(HttpServletRequest req) {
        Long orgId = orgId(req);
        return R.ok(orgService.getOrg(orgId));
    }

    // ─── 数据大屏 ────────────────────────────────────────────

    /** GET /api/v1/org/dashboard */
    @GetMapping("/dashboard")
    public R<?> dashboard(HttpServletRequest req) {
        return R.ok(orgService.dashboard(orgId(req)));
    }

    // ─── 点位管理 ────────────────────────────────────────────

    /** GET /api/v1/org/locations */
    @GetMapping("/locations")
    public R<?> listLocations(HttpServletRequest req) {
        return R.ok(orgService.listLocations(orgId(req)));
    }

    /** POST /api/v1/org/locations */
    @PostMapping("/locations")
    public R<?> createLocation(@RequestBody EduLocation loc, HttpServletRequest req) {
        loc.setId(null);
        return R.ok(orgService.saveLocation(orgId(req), loc));
    }

    /** PUT /api/v1/org/locations/{id} */
    @PutMapping("/locations/{id}")
    public R<?> updateLocation(@PathVariable Long id, @RequestBody EduLocation loc,
                               HttpServletRequest req) {
        loc.setId(id);
        try {
            return R.ok(orgService.saveLocation(orgId(req), loc));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /** DELETE /api/v1/org/locations/{id} */
    @DeleteMapping("/locations/{id}")
    public R<?> deleteLocation(@PathVariable Long id, HttpServletRequest req) {
        try {
            orgService.deleteLocation(orgId(req), id);
            return R.ok(null);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    // ─── 打卡记录 ────────────────────────────────────────────

    /**
     * GET /api/v1/org/checkins?page=1&size=20&locationId=&period=&status=
     */
    @GetMapping("/checkins")
    public R<?> pageCheckins(@RequestParam(defaultValue = "1")  int page,
                              @RequestParam(defaultValue = "20") int size,
                              @RequestParam(required = false) Long    locationId,
                              @RequestParam(required = false) String  period,
                              @RequestParam(required = false) Integer status,
                              HttpServletRequest req) {
        return R.ok(orgService.pageCheckins(orgId(req), page, size, locationId, period, status));
    }

    // ─── 员工列表 ────────────────────────────────────────────

    /** GET /api/v1/org/workers */
    @GetMapping("/workers")
    public R<?> listWorkers(HttpServletRequest req) {
        return R.ok(orgService.listWorkers(orgId(req)));
    }

    // ─── 礼品管理 ────────────────────────────────────────────

    /** GET /api/v1/org/gifts — 本机构礼品列表 */
    @GetMapping("/gifts")
    public R<?> listGifts(HttpServletRequest req) {
        return R.ok(giftService.listByOrg(orgId(req)));
    }

    /** POST /api/v1/org/gifts */
    @PostMapping("/gifts")
    public R<?> createGift(@RequestBody GiftItem gift, HttpServletRequest req) {
        gift.setId(null);
        return R.ok(giftService.save(gift, orgId(req)));
    }

    /** PUT /api/v1/org/gifts/{id} */
    @PutMapping("/gifts/{id}")
    public R<?> updateGift(@PathVariable Long id, @RequestBody GiftItem gift,
                            HttpServletRequest req) {
        gift.setId(id);
        try {
            return R.ok(giftService.save(gift, orgId(req)));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /** DELETE /api/v1/org/gifts/{id} */
    @DeleteMapping("/gifts/{id}")
    public R<?> deleteGift(@PathVariable Long id, HttpServletRequest req) {
        try {
            giftService.delete(id, orgId(req));
            return R.ok(null);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }


    // ─── 打卡审核（机构端）────────────────────────────────────────

    /** GET /api/v1/org/checkins/pending-review?reviewStatus=0&page=1&size=20 */
    @GetMapping("/checkins/pending-review")
    public R<?> pendingReview(@RequestParam(defaultValue = "0")  int reviewStatus,
                              @RequestParam(defaultValue = "1")  int page,
                              @RequestParam(defaultValue = "20") int size,
                              HttpServletRequest req) {
        Long orgId = orgId(req);
        List<Long> locationIds = orgService.listIdsByOrg(orgId);
        if (locationIds.isEmpty()) return R.ok(java.util.Map.of("list", java.util.List.of(), "total", 0));
        int offset = (page - 1) * size;
        LambdaQueryWrapper<CheckinRecord> qw = new LambdaQueryWrapper<CheckinRecord>()
                .in(CheckinRecord::getLocationId, locationIds)
                .eq(CheckinRecord::getReviewStatus, reviewStatus)
                .orderByDesc(CheckinRecord::getCheckinTime)
                .last("LIMIT " + size + " OFFSET " + offset);
        long cnt = checkinMapper.selectCount(new LambdaQueryWrapper<CheckinRecord>()
                .in(CheckinRecord::getLocationId, locationIds)
                .eq(CheckinRecord::getReviewStatus, reviewStatus));
        List<Map<String, Object>> rows = checkinMapper.selectList(qw).stream().map(r -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", r.getId()); m.put("userId", r.getUserId());
            m.put("locationId", r.getLocationId()); m.put("photoUrl", r.getPhotoUrl());
            m.put("checkinTime", r.getCheckinTime()); m.put("pointsEarned", r.getPointsEarned());
            m.put("reviewStatus", r.getReviewStatus()); m.put("reviewRemark", r.getReviewRemark());
            var u = userMapper.selectById(r.getUserId());
            m.put("userPhone", u != null ? u.getPhone() : "");
            m.put("userName", u != null ? (u.getRealName() != null ? u.getRealName() : u.getNickName()) : "");
            return m;
        }).toList();
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("list", rows); data.put("total", cnt); data.put("page", page); data.put("size", size);
        return R.ok(data);
    }

    /** POST /api/v1/org/checkins/{id}/review  body: { action, remark } */
    @PostMapping("/checkins/{id}/review")
    @Transactional
    public R<?> reviewCheckin(@PathVariable Long id,
                              @RequestBody Map<String, String> body,
                              HttpServletRequest req) {
        Long orgId = orgId(req);
        List<Long> locationIds = orgService.listIdsByOrg(orgId);
        CheckinRecord record = checkinMapper.selectById(id);
        if (record == null || !locationIds.contains(record.getLocationId()))
            return R.fail("记录不存在或无权限");
        if (record.getReviewStatus() != null && record.getReviewStatus() != 0)
            return R.fail("该记录已审核");
        String action = body.getOrDefault("action", "approve");
        String remark = body.getOrDefault("remark", "");
        CheckinRecord update = new CheckinRecord();
        update.setId(id); update.setReviewRemark(remark);
        if ("approve".equals(action)) {
            update.setReviewStatus(1); update.setStatus(1);
        } else {
            update.setReviewStatus(2); update.setStatus(0);
            if (record.getPointsEarned() != null && record.getPointsEarned() > 0) {
                com.chocwell.jy.entity.SysUser u = userMapper.selectById(record.getUserId());
                if (u != null) {
                    com.chocwell.jy.entity.SysUser uUpd = new com.chocwell.jy.entity.SysUser();
                    uUpd.setId(u.getId());
                    uUpd.setTotalPoints(Math.max(0, u.getTotalPoints() - record.getPointsEarned()));
                    userMapper.updateById(uUpd);
                    com.chocwell.jy.entity.PointsLog log = new com.chocwell.jy.entity.PointsLog();
                    log.setUserId(record.getUserId()); log.setType(3);
                    log.setAmount(-record.getPointsEarned());
                    log.setRemark("打卡审核驳回：" + remark); log.setRelatedId(id);
                    pointsLogMapper.insert(log);
                }
            }
        }
        checkinMapper.updateById(update);
        return R.ok(null);
    }

    // ─── 打卡规则（机构级配置）────────────────────────────────────

    /**
     * GET /api/v1/org/checkin-config
     * 返回当前机构的配置项 + 平台默认值
     */
    @GetMapping("/checkin-config")
    public R<?> getCheckinConfig(HttpServletRequest req) {
        return R.ok(orgConfigService.listEffective(orgId(req)));
    }

    /**
     * PUT /api/v1/org/checkin-config
     * body: { points_per_checkin: "20", max_checkins_per_period: "10", current_period_type: "MONTH" }
     * value 为空字符串视为「清空 → 回退平台默认」
     */
    @PutMapping("/checkin-config")
    public R<?> updateCheckinConfig(@RequestBody Map<String, String> body, HttpServletRequest req) {
        Long orgId = orgId(req);
        for (Map.Entry<String, String> e : body.entrySet()) {
            try {
                orgConfigService.set(orgId, e.getKey(), e.getValue());
            } catch (IllegalArgumentException ex) {
                return R.fail(ex.getMessage());
            }
        }
        return R.ok(orgConfigService.listEffective(orgId));
    }

    // ─── Excel 导出 ────────────────────────────────────────────

    /** GET /api/v1/org/checkins/export */
    @GetMapping("/checkins/export")
    public void exportCheckins(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long orgId = orgId(req);
        setExcelHeader(resp, "打卡记录");
        var result = orgService.pageCheckins(orgId, 1, 10000, null, null, null);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("list");
        List<OrgCheckinRow> data = rows == null ? List.of() : rows.stream().map(r -> {
            OrgCheckinRow row = new OrgCheckinRow();
            row.setUserPhone(str(r.get("phone")));
            row.setUserName(firstNonEmpty(r.get("realName"), r.get("nickName"), r.get("phone")));
            row.setLocationName(str(r.get("locationName")));
            row.setCheckinTime(formatTime(r.get("checkinTime")));
            row.setPoints(toInt(r.get("pointsEarned")));
            row.setStatus(toInt(r.get("status")) == 1 ? "正常" : "异常");
            return row;
        }).toList();
        EasyExcel.write(resp.getOutputStream(), OrgCheckinRow.class).sheet("打卡记录").doWrite(data);
    }

    // ─── 工具 ─────────────────────────────────────────────────

    private Long orgId(HttpServletRequest req) {
        return (Long) req.getAttribute(OrgAuthInterceptor.ORG_ID_KEY);
    }

    private void setExcelHeader(HttpServletResponse resp, String filename) throws IOException {
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String encoded = URLEncoder.encode(filename + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        resp.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }

    private String str(Object o) { return o == null ? "" : o.toString(); }
    private int toInt(Object o)  { return o == null ? 0 : Integer.parseInt(o.toString()); }
    private String firstNonEmpty(Object... vals) {
        for (Object v : vals) {
            if (v == null) continue;
            String s = v.toString();
            if (!s.isEmpty()) return s;
        }
        return "";
    }
    private String formatTime(Object o) {
        if (o == null) return "";
        String s = o.toString().replace("T", " ");
        return s.length() >= 16 ? s.substring(0, 16) : s;
    }

    @Data static class OrgCheckinRow {
        @ExcelProperty("手机号")   private String userPhone;
        @ExcelProperty("姓名")     private String userName;
        @ExcelProperty("点位")     private String locationName;
        @ExcelProperty("打卡时间") private String checkinTime;
        @ExcelProperty("积分")     private int    points;
        @ExcelProperty("状态")     private String status;
    }
}
