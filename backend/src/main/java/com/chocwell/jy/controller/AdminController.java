package com.chocwell.jy.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chocwell.jy.entity.Article;
import com.chocwell.jy.entity.CheckinRecord;
import com.chocwell.jy.entity.EduLocation;
import com.chocwell.jy.entity.GiftItem;
import com.chocwell.jy.entity.Organization;
import com.chocwell.jy.entity.RedeemRecord;
import com.chocwell.jy.entity.SysAdmin;
import com.chocwell.jy.interceptor.AdminAuthInterceptor;
import com.chocwell.jy.mapper.CheckinMapper;
import com.chocwell.jy.mapper.LocationMapper;
import com.chocwell.jy.mapper.PointsLogMapper;
import com.chocwell.jy.mapper.OrgMapper;
import com.chocwell.jy.mapper.RedeemMapper;
import com.chocwell.jy.mapper.UserMapper;
import com.chocwell.jy.service.*;
import com.chocwell.jy.service.WxService;
import com.chocwell.jy.util.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import org.springframework.cache.CacheManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 管理后台 API（统一前缀 /api/v1/admin）
 * 除 /login 外均需管理员 token（AdminAuthInterceptor 处理）
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService    adminService;
    private final LocationService locationService;
    private final WxService       wxService;
    private final ArticleService  articleService;
    private final CheckinService  checkinService;
    private final RedeemService   redeemService;
    private final ConfigService   configService;
    private final OssService      ossService;
    private final UserMapper      userMapper;
    private final CheckinMapper   checkinMapper;
    private final LocationMapper  locationMapper;
    private final OrgMapper       orgMapper;
    private final GiftService     giftService;
    private final RedeemMapper    redeemMapper;
    private final PointsLogMapper  pointsLogMapper;
    private final CacheManager    cacheManager;

    // ─── 工具方法 ─────────────────────────────────────────────

    /** 将前端可能传来的空字符串 BigDecimal 字段统一转 null */
    private static BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        String s = val.toString().trim();
        if (s.isEmpty()) return null;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
    }

    // ─── 缓存管理 ─────────────────────────────────────────────

    /**
     * 手动清空全部 Redis 缓存（后台数据更新后如果用户端未及时刷新，可调用此接口）
     * POST /api/v1/admin/cache/clear
     */
    @PostMapping("/cache/clear")
    public R<?> clearCache() {
        cacheManager.getCacheNames().forEach(name ->
            Optional.ofNullable(cacheManager.getCache(name)).ifPresent(org.springframework.cache.Cache::clear));
        return R.ok("全部缓存已清除");
    }

    // ─── 图片上传 ─────────────────────────────────────────────

    /**
     * 上传图片（礼品图等）
     * POST /api/v1/admin/upload  multipart/form-data  file=...
     * 走 OssService：配置 OSS 自动上传到云端，否则走本地 /app/uploads
     * 返回：{ url: "https://..." 或 "/api/v1/files/{filename}" }
     */
    @PostMapping("/upload")
    public R<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return R.fail("文件为空");
        String ct = file.getContentType() != null ? file.getContentType() : "";
        if (!ct.startsWith("image/")) return R.fail("只支持上传图片文件");
        return R.ok(Map.of("url", ossService.upload(file, "admin")));
    }

    // ─── 登录 ────────────────────────────────────────────────

    /**
     * 管理员登录
     * POST /api/v1/admin/login
     * body: { username, password }
     */
    @PostMapping("/login")
    public R<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return R.fail("用户名和密码不能为空");
        }
        try {
            Map<String, Object> data = adminService.login(username, password);
            return R.ok(data);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    // ─── 数据大屏 ────────────────────────────────────────────

    /**
     * 管理后台数据大屏
     * GET /api/v1/admin/dashboard
     */
    @GetMapping("/dashboard")
    public R<?> dashboard() {
        Map<String, Object> data = new HashMap<>();
        int totalUsers = userMapper.countAll();

        data.put("totalUsers",       totalUsers);
        data.put("todayNew",         userMapper.countTodayNew());
        data.put("totalCheckins",    checkinMapper.countAllValid());
        data.put("todayCheckins",    checkinMapper.countToday());
        data.put("monthCheckins",    checkinMapper.countThisMonth());
        data.put("activeLocations",  locationService.countActive());
        data.put("trend7Days",       checkinMapper.last7DaysTrend());
        data.put("trend30Days",      checkinMapper.last30DaysTrend());
        data.put("districtRanking",  checkinMapper.districtCoverageRanking());
        data.put("mapLocations",     locationMapper.activeLocationMap());
        data.put("recentCheckins",   checkinMapper.recentCheckins(10));
        return R.ok(data);
    }

    // ─── 打卡记录 ────────────────────────────────────────────

    /**
     * 管理后台打卡记录分页
     * GET /api/v1/admin/checkins?page=1&size=20&userId=&locationId=&period=&status=
     */
    @GetMapping("/checkins")
    public R<?> pageCheckins(@RequestParam(defaultValue = "1")  int page,
                             @RequestParam(defaultValue = "20") int size,
                             @RequestParam(required = false) Long    userId,
                             @RequestParam(required = false) Long    locationId,
                             @RequestParam(required = false) String  period,
                             @RequestParam(required = false) Integer status) {
        return R.ok(checkinService.pageAdmin(page, size, userId, locationId, period, status));
    }

    // ─── 系统配置 ────────────────────────────────────────────

    /**
     * 获取全部配置
     * GET /api/v1/admin/configs
     */
    @GetMapping("/configs")
    public R<?> listConfigs() {
        return R.ok(configService.listAll());
    }

    /**
     * 更新配置
     * PUT /api/v1/admin/configs/{key}
     * body: { value }
     */
    @PutMapping("/configs/{key}")
    public R<?> updateConfig(@PathVariable String key,
                             @RequestBody Map<String, String> body) {
        configService.set(key, body.get("value"));
        return R.ok(null);
    }

    // ─── 点位管理 ────────────────────────────────────────────

    /**
     * GET /api/v1/admin/locations
     */
    @GetMapping("/locations")
    public R<?> listLocations(@RequestParam(required = false) Long orgId) {
        if (orgId != null) {
            return R.ok(locationService.listByOrg(orgId));
        }
        return R.ok(locationService.listAll());
    }

    /**
     * GET /api/v1/admin/locations/{id}
     */
    @GetMapping("/locations/{id}")
    public R<?> getLocation(@PathVariable Long id) {
        return R.ok(locationService.getById(id));
    }

    /**
     * POST /api/v1/admin/locations
     */
    @PostMapping("/locations")
    public R<?> createLocation(@RequestBody EduLocation location) {
        location.setId(null); // 确保是新增
        locationService.save(location);
        return R.ok(location);
    }

    /**
     * PUT /api/v1/admin/locations/{id}
     */
    @PutMapping("/locations/{id}")
    public R<?> updateLocation(@PathVariable Long id,
                               @RequestBody EduLocation location) {
        location.setId(id);
        locationService.save(location);
        return R.ok(null);
    }

    /**
     * DELETE /api/v1/admin/locations/{id}  （逻辑关闭）
     */
    @DeleteMapping("/locations/{id}")
    public R<?> deleteLocation(@PathVariable Long id) {
        locationService.delete(id);
        return R.ok(null);
    }

    // ─── 文章管理 ────────────────────────────────────────────

    /**
     * GET /api/v1/admin/articles?page=1&size=10&type=1&keyword=xxx
     */
    @GetMapping("/articles")
    public R<?> pageArticles(@RequestParam(defaultValue = "1")  int page,
                             @RequestParam(defaultValue = "10") int size,
                             @RequestParam(required = false) Integer type,
                             @RequestParam(required = false) String keyword) {
        return R.ok(articleService.pageAdmin(page, size, type, keyword));
    }

    /**
     * POST /api/v1/admin/articles
     */
    @PostMapping("/articles")
    public R<?> createArticle(@RequestBody Article article) {
        article.setId(null);
        articleService.save(article);
        return R.ok(article);
    }

    /**
     * PUT /api/v1/admin/articles/{id}
     */
    @PutMapping("/articles/{id}")
    public R<?> updateArticle(@PathVariable Long id, @RequestBody Article article) {
        article.setId(id);
        articleService.save(article);
        return R.ok(null);
    }

    /**
     * DELETE /api/v1/admin/articles/{id}
     */
    @DeleteMapping("/articles/{id}")
    public R<?> deleteArticle(@PathVariable Long id) {
        articleService.delete(id);
        return R.ok(null);
    }

    // ─── 用户管理 ────────────────────────────────────────────

    /**
     * GET /api/v1/admin/users?page=1&size=10&keyword=xxx
     */
    @GetMapping("/users")
    public R<?> pageUsers(@RequestParam(defaultValue = "1")  int page,
                          @RequestParam(defaultValue = "10") int size,
                          @RequestParam(required = false) String keyword) {
        return R.ok(adminService.pageUsers(page, size, keyword));
    }

    /**
     * GET /api/v1/admin/users/{userId}
     */
    @GetMapping("/users/{userId}")
    public R<?> getUser(@PathVariable Long userId) {
        return R.ok(adminService.getUser(userId));
    }

    /**
     * GET /api/v1/admin/users/{userId}/checkins
     */
    @GetMapping("/users/{userId}/checkins")
    public R<?> userCheckins(@PathVariable Long userId) {
        return R.ok(checkinService.history(userId));
    }

    /**
     * 调整积分
     * POST /api/v1/admin/users/{userId}/adjust-points
     * body: { delta, remark }  delta 可正可负
     */
    @PostMapping("/users/{userId}/adjust-points")
    public R<?> adjustPoints(@PathVariable Long userId,
                             @RequestBody Map<String, Object> body) {
        int delta = Integer.parseInt(body.get("delta").toString());
        String remark = (String) body.get("remark");
        try {
            adminService.adjustPoints(userId, delta, remark);
            return R.ok(null);
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    // ─── 积分核销 ────────────────────────────────────────────

    /**
     * 核销用户积分
     * POST /api/v1/admin/redeem
     * body: { userId, locationId, points, remark }
     */
    @PostMapping("/redeem")
    public R<?> redeem(@RequestBody Map<String, Object> body,
                       HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute(AdminAuthInterceptor.ADMIN_ID_KEY);
        Long userId     = Long.valueOf(body.get("userId").toString());
        Long locationId = Long.valueOf(body.get("locationId").toString());
        int  points     = Integer.parseInt(body.get("points").toString());
        String remark   = (String) body.get("remark");
        try {
            return R.ok(redeemService.redeem(userId, locationId, points, operatorId, remark));
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 近期核销记录
     * GET /api/v1/admin/redeem/records?locationId=xxx&limit=20
     */
    @GetMapping("/redeem/records")
    public R<?> redeemRecords(@RequestParam(required = false) Long locationId,
                              @RequestParam(defaultValue = "20") int limit) {
        return R.ok(redeemService.listRecent(locationId, limit));
    }

    // ─── 礼品管理（平台级） ───────────────────────────────────

    /** GET /api/v1/admin/gifts — 所有礼品（含机构礼品） */
    @GetMapping("/gifts")
    public R<?> listGifts() {
        return R.ok(giftService.listAll());
    }

    /** POST /api/v1/admin/gifts */
    @PostMapping("/gifts")
    public R<?> createGift(@RequestBody GiftItem gift) {
        gift.setId(null);
        return R.ok(giftService.save(gift, null));
    }

    /** PUT /api/v1/admin/gifts/{id} */
    @PutMapping("/gifts/{id}")
    public R<?> updateGift(@PathVariable Long id, @RequestBody GiftItem gift) {
        gift.setId(id);
        return R.ok(giftService.save(gift, null));
    }

    /** DELETE /api/v1/admin/gifts/{id} */
    @DeleteMapping("/gifts/{id}")
    public R<?> deleteGift(@PathVariable Long id) {
        giftService.delete(id, null);
        return R.ok(null);
    }

    // ─── 机构管理（超管功能） ─────────────────────────────────

    /** GET /api/v1/admin/orgs */
    @GetMapping("/orgs")
    public R<?> listOrgs() {
        return R.ok(orgMapper.selectList(null));
    }

    /** POST /api/v1/admin/orgs */
    @PostMapping("/orgs")
    public R<?> createOrg(@RequestBody Organization org) {
        org.setId(null);
        orgMapper.insert(org);
        return R.ok(org);
    }

    /** PUT /api/v1/admin/orgs/{id} */
    @PutMapping("/orgs/{id}")
    public R<?> updateOrg(@PathVariable Long id, @RequestBody Organization org) {
        org.setId(id);
        orgMapper.updateById(org);
        return R.ok(null);
    }

    /**
     * 为机构创建管理员账号
     * POST /api/v1/admin/orgs/{orgId}/admins
     * body: { username, password, realName }
     */
    @PostMapping("/orgs/{orgId}/admins")
    public R<?> createOrgAdmin(@PathVariable Long orgId,
                               @RequestBody Map<String, String> body) {
        Organization org = orgMapper.selectById(orgId);
        if (org == null) return R.fail("机构不存在");
        SysAdmin admin = new SysAdmin();
        admin.setUsername(body.get("username"));
        admin.setRealName(body.get("realName"));
        admin.setRole("ORG_ADMIN");
        admin.setOrgId(orgId);
        admin.setStatus(1);
        try {
            adminService.createAdmin(admin, body.get("password"));
            return R.ok(null);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    // ─── 兑换记录查询 ──────────────────────────────────────────────

    /**
     * 分页查询全部兑换记录
     * GET /api/v1/admin/redeems?userId=&giftId=&keyword=&page=1&size=20
     */
    @GetMapping("/redeems")
    public R<?> listRedeems(@RequestParam(required = false) Long userId,
                            @RequestParam(required = false) Long giftId,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        LambdaQueryWrapper<RedeemRecord> qw = new LambdaQueryWrapper<RedeemRecord>()
                .eq(userId != null, RedeemRecord::getUserId, userId)
                .eq(giftId != null, RedeemRecord::getGiftId, giftId)
                .like(keyword != null && !keyword.isBlank(), RedeemRecord::getGiftName, keyword)
                .orderByDesc(RedeemRecord::getCreateTime)
                .last("LIMIT " + size + " OFFSET " + offset);
        LambdaQueryWrapper<RedeemRecord> countQw = new LambdaQueryWrapper<RedeemRecord>()
                .eq(userId != null, RedeemRecord::getUserId, userId)
                .eq(giftId != null, RedeemRecord::getGiftId, giftId)
                .like(keyword != null && !keyword.isBlank(), RedeemRecord::getGiftName, keyword);
        long total = redeemMapper.selectCount(countQw);
        List<RedeemRecord> list = redeemMapper.selectList(qw);
        // 附加用户手机号
        List<Map<String, Object>> result = list.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id",         r.getId());
            m.put("userId",     r.getUserId());
            m.put("giftId",     r.getGiftId());
            m.put("giftName",   r.getGiftName());
            m.put("pointsUsed", r.getPointsUsed());
            m.put("remark",     r.getRemark());
            m.put("createTime", r.getCreateTime());
            var user = userMapper.selectById(r.getUserId());
            m.put("userPhone",  user != null ? user.getPhone() : "");
            m.put("userName",   user != null ? (user.getRealName() != null ? user.getRealName() : user.getNickName()) : "");
            return m;
        }).toList();
        Map<String, Object> data = new HashMap<>();
        data.put("list", result);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return R.ok(data);
    }

    // ─── Excel 导出 ────────────────────────────────────────────────

    /** 打卡记录导出 */
    @GetMapping("/checkins/export")
    public void exportCheckins(@RequestParam(required = false) Long userId,
                               @RequestParam(required = false) Long locationId,
                               @RequestParam(required = false) String period,
                               @RequestParam(required = false) Integer status,
                               HttpServletResponse response) throws IOException {
        setExcelHeader(response, "打卡记录");
        List<Map<String, Object>> rows = checkinService.pageAdmin(
                1, 10000, userId, locationId, period, status).entrySet()
                .stream().filter(e -> e.getKey().equals("list"))
                .map(e -> (List<Map<String,Object>>) e.getValue())
                .findFirst().orElse(List.of());
        List<CheckinExcelRow> data = rows.stream().map(r -> {
            CheckinExcelRow row = new CheckinExcelRow();
            row.setId(toLong(r.get("id")));
            // SQL 列名经 mybatis underscore→camel：phone / realName / nickName / locationName / checkinTime / pointsEarned / status
            row.setUserPhone(str(r.get("phone")));
            row.setUserName(firstNonEmpty(r.get("realName"), r.get("nickName"), r.get("phone")));
            row.setLocationName(str(r.get("locationName")));
            row.setCheckinTime(formatTime(r.get("checkinTime")));
            row.setPoints(toInt(r.get("pointsEarned")));
            row.setStatus(toInt(r.get("status")) == 1 ? "正常" : "异常");
            return row;
        }).toList();
        EasyExcel.write(response.getOutputStream(), CheckinExcelRow.class).sheet("打卡记录").doWrite(data);
    }

    /** 兑换记录导出 */
    @GetMapping("/redeems/export")
    public void exportRedeems(@RequestParam(required = false) Long userId,
                              @RequestParam(required = false) String keyword,
                              HttpServletResponse response) throws IOException {
        setExcelHeader(response, "兑换记录");
        LambdaQueryWrapper<RedeemRecord> qw = new LambdaQueryWrapper<RedeemRecord>()
                .eq(userId != null, RedeemRecord::getUserId, userId)
                .like(keyword != null && !keyword.isBlank(), RedeemRecord::getGiftName, keyword)
                .orderByDesc(RedeemRecord::getCreateTime);
        List<RedeemRecord> list = redeemMapper.selectList(qw);
        List<RedeemExcelRow> data = list.stream().map(r -> {
            RedeemExcelRow row = new RedeemExcelRow();
            row.setId(r.getId());
            row.setUserId(r.getUserId());
            var user = userMapper.selectById(r.getUserId());
            row.setUserPhone(user != null ? user.getPhone() : "");
            row.setUserName(user != null ? (user.getRealName() != null ? user.getRealName() : user.getNickName()) : "");
            row.setGiftName(r.getGiftName());
            row.setPointsUsed(r.getPointsUsed());
            row.setRemark(r.getRemark());
            row.setCreateTime(r.getCreateTime() != null ? r.getCreateTime().toString().replace("T", " ").substring(0, 16) : "");
            return row;
        }).toList();
        EasyExcel.write(response.getOutputStream(), RedeemExcelRow.class).sheet("兑换记录").doWrite(data);
    }

    // ─── 点位小程序码 ──────────────────────────────────────────────

    /**
     * 生成点位打卡二维码（小程序无限制码）
     * GET /api/v1/admin/locations/{id}/qrcode
     * 返回 PNG 图片流，前端直接用 <img src="..."> 或触发下载
     */
    @GetMapping("/locations/{id}/qrcode")
    public void locationQrCode(@PathVariable Long id, HttpServletResponse response) throws IOException {
        try {
            byte[] png = wxService.getUnlimitedQrCode("lid=" + id, "pages/checkin/form");
            response.setContentType("image/png");
            response.setHeader("Content-Disposition",
                    "inline; filename=\"location_" + id + "_qrcode.png\"");
            response.getOutputStream().write(png);
        } catch (Exception e) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(500);
            response.getWriter().write("{\"msg\":\"" + e.getMessage().replace("\"","'") + "\"}");
        }
    }

    private void setExcelHeader(HttpServletResponse response, String filename) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String encoded = URLEncoder.encode(filename + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }

    private String str(Object o) { return o == null ? "" : o.toString(); }
    private Long toLong(Object o) { return o == null ? 0L : Long.parseLong(o.toString()); }
    private int toInt(Object o) { return o == null ? 0 : Integer.parseInt(o.toString()); }
    /** 依次取第一个非空（且转字符串非空）的值 */
    private String firstNonEmpty(Object... vals) {
        for (Object v : vals) {
            if (v == null) continue;
            String s = v.toString();
            if (!s.isEmpty()) return s;
        }
        return "";
    }
    /** LocalDateTime / Timestamp / String 统一格式化为 "yyyy-MM-dd HH:mm" */
    private String formatTime(Object o) {
        if (o == null) return "";
        String s = o.toString().replace("T", " ");
        return s.length() >= 16 ? s.substring(0, 16) : s;
    }

    @Data static class CheckinExcelRow {
        @ExcelProperty("ID")           private Long id;
        @ExcelProperty("手机号")       private String userPhone;
        @ExcelProperty("姓名")         private String userName;
        @ExcelProperty("点位")         private String locationName;
        @ExcelProperty("打卡时间")     private String checkinTime;
        @ExcelProperty("积分")         private int points;
        @ExcelProperty("状态")         private String status;
    }

    @Data static class RedeemExcelRow {
        @ExcelProperty("ID")           private Long id;
        @ExcelProperty("用户ID")       private Long userId;
        @ExcelProperty("手机号")       private String userPhone;
        @ExcelProperty("姓名")         private String userName;
        @ExcelProperty("礼品名称")     private String giftName;
        @ExcelProperty("消耗积分")     private int pointsUsed;
        @ExcelProperty("备注")         private String remark;
        @ExcelProperty("兑换时间")     private String createTime;
    }

    // ─── 打卡审核 ──────────────────────────────────────────────────

    /**
     * 待审核打卡列表
     * GET /api/v1/admin/checkins/pending-review?page=1&size=20&reviewStatus=0
     */
    @GetMapping("/checkins/pending-review")
    public R<?> pendingReview(@RequestParam(defaultValue = "1")  int page,
                              @RequestParam(defaultValue = "20") int size,
                              @RequestParam(defaultValue = "0")  int reviewStatus) {
        int offset = (page - 1) * size;
        LambdaQueryWrapper<CheckinRecord> qw = new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getReviewStatus, reviewStatus)
                .orderByDesc(CheckinRecord::getCheckinTime)
                .last("LIMIT " + size + " OFFSET " + offset);
        long cnt = checkinMapper.selectCount(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getReviewStatus, reviewStatus));
        List<Map<String, Object>> rows = checkinMapper.selectList(qw).stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id",           r.getId());
            m.put("userId",       r.getUserId());
            m.put("locationId",   r.getLocationId());
            m.put("photoUrl",     r.getPhotoUrl());
            m.put("checkinTime",  r.getCheckinTime());
            m.put("pointsEarned", r.getPointsEarned());
            m.put("reviewStatus", r.getReviewStatus());
            m.put("reviewRemark", r.getReviewRemark());
            var u = userMapper.selectById(r.getUserId());
            m.put("userPhone", u != null ? u.getPhone() : "");
            m.put("userName",  u != null ? (u.getRealName() != null ? u.getRealName() : u.getNickName()) : "");
            return m;
        }).toList();
        Map<String, Object> data = new HashMap<>();
        data.put("list", rows); data.put("total", cnt); data.put("page", page); data.put("size", size);
        return R.ok(data);
    }

    /**
     * 审核通过 / 驳回
     * POST /api/v1/admin/checkins/{id}/review
     * body: { action: "approve"|"reject", remark: "..." }
     */
    @PostMapping("/checkins/{id}/review")
    @org.springframework.transaction.annotation.Transactional
    public R<?> reviewCheckin(@PathVariable Long id,
                              @RequestBody Map<String, String> body) {
        String action = body.getOrDefault("action", "approve");
        String remark = body.getOrDefault("remark", "");
        CheckinRecord record = checkinMapper.selectById(id);
        if (record == null) return R.fail("打卡记录不存在");
        if (record.getReviewStatus() != null && record.getReviewStatus() != 0)
            return R.fail("该记录已审核");

        CheckinRecord update = new CheckinRecord();
        update.setId(id);
        update.setReviewRemark(remark);

        if ("approve".equals(action)) {
            update.setReviewStatus(1);
            update.setStatus(1);
        } else {
            update.setReviewStatus(2);
            update.setStatus(0);
            if (record.getPointsEarned() != null && record.getPointsEarned() > 0) {
                com.chocwell.jy.entity.SysUser u = userMapper.selectById(record.getUserId());
                if (u != null) {
                    com.chocwell.jy.entity.SysUser uUpd = new com.chocwell.jy.entity.SysUser();
                    uUpd.setId(u.getId());
                    uUpd.setTotalPoints(Math.max(0, u.getTotalPoints() - record.getPointsEarned()));
                    userMapper.updateById(uUpd);
                    com.chocwell.jy.entity.PointsLog log = new com.chocwell.jy.entity.PointsLog();
                    log.setUserId(record.getUserId());
                    log.setType(3);
                    log.setAmount(-record.getPointsEarned());
                    log.setRemark("打卡审核驳回：" + remark);
                    log.setRelatedId(id);
                    pointsLogMapper.insert(log);
                }
            }
        }
        checkinMapper.updateById(update);
        return R.ok(null);
    }

    // ─── 实名认证审核 ─────────────────────────────────────────

    /**
     * 查询实名认证列表
     * GET /api/v1/admin/verifications?status=1&page=1&size=20
     * status: 0=全部, 1=审核中, 2=已通过, 3=已拒绝
     */
    @GetMapping("/verifications")
    public R<?> listVerifications(
            @RequestParam(defaultValue = "1") int status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;

        // 用两个独立 wrapper，避免同一对象复用导致 PostgreSQL 查询异常
        var countWrapper = new LambdaQueryWrapper<com.chocwell.jy.entity.SysUser>();
        var listWrapper  = new LambdaQueryWrapper<com.chocwell.jy.entity.SysUser>()
                .orderByDesc(com.chocwell.jy.entity.SysUser::getUpdateTime);
        if (status > 0) {
            countWrapper.eq(com.chocwell.jy.entity.SysUser::getVerifyStatus, status);
            listWrapper .eq(com.chocwell.jy.entity.SysUser::getVerifyStatus, status);
        } else {
            countWrapper.gt(com.chocwell.jy.entity.SysUser::getVerifyStatus, 0);
            listWrapper .gt(com.chocwell.jy.entity.SysUser::getVerifyStatus, 0);
        }
        long total = userMapper.selectCount(countWrapper);
        var list = userMapper.selectList(listWrapper.last("LIMIT " + size + " OFFSET " + offset));
        var result = list.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("userId",             u.getId());
            m.put("realName",           u.getRealName());
            m.put("idCard",             maskIdCard(u.getIdCard()));
            m.put("phone",              u.getPhone());
            m.put("verifyStatus",       u.getVerifyStatus());
            m.put("verifyRejectReason", u.getVerifyRejectReason());
            m.put("updateTime",         u.getUpdateTime());
            return m;
        }).toList();
        Map<String, Object> data = new HashMap<>();
        data.put("list",  result);
        data.put("total", total);
        data.put("page",  page);
        data.put("size",  size);
        return R.ok(data);
    }

    /**
     * 审核通过
     * POST /api/v1/admin/verifications/{userId}/approve
     */
    @PostMapping("/verifications/{userId}/approve")
    public R<?> approveVerification(@PathVariable Long userId) {
        com.chocwell.jy.entity.SysUser user = userMapper.selectById(userId);
        if (user == null) return R.fail("用户不存在");
        com.chocwell.jy.entity.SysUser upd = new com.chocwell.jy.entity.SysUser();
        upd.setId(userId);
        upd.setVerifyStatus(2);
        upd.setVerifyRejectReason(null);
        userMapper.updateById(upd);
        return R.ok(null);
    }

    /**
     * 审核拒绝
     * POST /api/v1/admin/verifications/{userId}/reject
     * body: { reason }
     */
    @PostMapping("/verifications/{userId}/reject")
    public R<?> rejectVerification(@PathVariable Long userId,
                                   @RequestBody Map<String, String> body) {
        com.chocwell.jy.entity.SysUser user = userMapper.selectById(userId);
        if (user == null) return R.fail("用户不存在");
        String reason = body.getOrDefault("reason", "信息不符，请重新提交");
        com.chocwell.jy.entity.SysUser upd = new com.chocwell.jy.entity.SysUser();
        upd.setId(userId);
        upd.setVerifyStatus(3);
        upd.setVerifyRejectReason(reason);
        userMapper.updateById(upd);
        return R.ok(null);
    }

    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) return idCard;
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

}
