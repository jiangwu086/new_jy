package com.chocwell.jy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chocwell.jy.entity.PointsLog;
import com.chocwell.jy.entity.RedeemRecord;
import com.chocwell.jy.entity.SysUser;
import com.chocwell.jy.interceptor.UserAuthInterceptor;
import com.chocwell.jy.mapper.PointsLogMapper;
import com.chocwell.jy.mapper.RedeemMapper;
import com.chocwell.jy.mapper.UserMapper;
import com.chocwell.jy.util.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户个人信息接口（小程序端，需登录）
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final PointsLogMapper pointsLogMapper;
    private final RedeemMapper redeemMapper;
    private final com.chocwell.jy.service.SmsService smsService;

    /**
     * 获取当前用户信息（含积分、认证状态）
     * GET /api/v1/user/me
     */
    @GetMapping("/me")
    public R<?> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(UserAuthInterceptor.USER_ID_KEY);
        SysUser user = userMapper.selectById(userId);
        if (user == null) return R.fail("用户不存在");

        int verifyStatus = user.getVerifyStatus() == null ? 0 : user.getVerifyStatus();
        // 兼容旧数据：realName 有值但 verifyStatus=0 → 视为已通过
        if (verifyStatus == 0 && user.getRealName() != null && !user.getRealName().isBlank()) {
            verifyStatus = 2;
        }
        boolean isVerified = verifyStatus == 2;

        // 身份证脱敏：保留前4位和后4位，中间用 * 替换
        String idCardMasked = null;
        if (user.getIdCard() != null && user.getIdCard().length() >= 8) {
            idCardMasked = user.getIdCard().replaceAll("(?<=.{4}).(?=.{4})", "*");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("userId",             user.getId());
        data.put("phone",              user.getPhone());
        data.put("nickName",           user.getNickName());
        data.put("avatarUrl",          user.getAvatarUrl());
        data.put("totalPoints",        user.getTotalPoints());
        data.put("usablePoints",       user.getTotalPoints());   // TODO: 可用积分独立字段
        data.put("realName",           user.getRealName());
        data.put("idCardMasked",       idCardMasked);            // 新增：脱敏身份证号
        data.put("verifyStatus",       verifyStatus);            // 0未提交 1审核中 2已通过 3已拒绝
        data.put("verifyRejectReason", user.getVerifyRejectReason());
        data.put("isVerified",         isVerified);
        return R.ok(data);
    }

    /**
     * 提交实名认证申请 → 状态变为「审核中」
     * PUT /api/v1/user/profile
     * body: { realName, idCard, nickName, avatarUrl }
     */
    @PutMapping("/profile")
    public R<?> updateProfile(@RequestBody Map<String, String> body,
                              HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(UserAuthInterceptor.USER_ID_KEY);
        SysUser update = new SysUser();
        update.setId(userId);
        if (body.containsKey("nickName"))  update.setNickName(body.get("nickName"));
        if (body.containsKey("avatarUrl")) update.setAvatarUrl(body.get("avatarUrl"));

        // 提交实名信息 → 置为「审核中」，清空上次拒绝原因
        if (body.containsKey("realName") || body.containsKey("idCard")) {
            if (body.containsKey("realName")) update.setRealName(body.get("realName"));
            if (body.containsKey("idCard"))   update.setIdCard(body.get("idCard"));
            update.setVerifyStatus(1);           // 1 = 审核中
            update.setVerifyRejectReason(null);
        }
        userMapper.updateById(update);
        return R.ok(null);
    }

    /**
     * 换绑手机号（需短信验证码）
     * POST /api/v1/user/change-phone
     * body: { phone, code }
     */
    @PostMapping("/change-phone")
    public R<?> changePhone(@RequestBody Map<String, String> body,
                            HttpServletRequest request) {
        String phone = body.get("phone");
        String code  = body.get("code");
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) return R.fail("手机号格式不正确");
        if (code  == null || code.length() != 6)                return R.fail("请输入6位验证码");
        if (!smsService.verifyCode(phone, code))                return R.fail("验证码错误或已过期");

        // 检查手机号是否已被其他账号占用
        Long userId = (Long) request.getAttribute(com.chocwell.jy.interceptor.UserAuthInterceptor.USER_ID_KEY);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser> qw =
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .ne(SysUser::getId, userId);
        if (userMapper.selectCount(qw) > 0) return R.fail("该手机号已被其他账号使用");

        SysUser update = new SysUser();
        update.setId(userId);
        update.setPhone(phone);
        userMapper.updateById(update);
        return R.ok(null);
    }

    /**
     * 积分流水
     * GET /api/v1/user/points-log?page=1&size=20
     */
    @GetMapping("/points-log")
    public R<?> pointsLog(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size,
                          HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(UserAuthInterceptor.USER_ID_KEY);
        int offset = (page - 1) * size;
        long total = pointsLogMapper.selectCount(
                new LambdaQueryWrapper<PointsLog>().eq(PointsLog::getUserId, userId));
        var list = pointsLogMapper.selectList(
                new LambdaQueryWrapper<PointsLog>()
                        .eq(PointsLog::getUserId, userId)
                        .orderByDesc(PointsLog::getCreateTime)
                        .last("LIMIT " + size + " OFFSET " + offset));
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return R.ok(data);
    }

    /**
     * 兑换记录
     * GET /api/v1/user/redeems?page=1&size=20
     */
    @GetMapping("/redeems")
    public R<?> redeems(@RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "20") int size,
                        HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(UserAuthInterceptor.USER_ID_KEY);
        int offset = (page - 1) * size;
        long total = redeemMapper.selectCount(
                new LambdaQueryWrapper<RedeemRecord>().eq(RedeemRecord::getUserId, userId));
        var list = redeemMapper.selectList(
                new LambdaQueryWrapper<RedeemRecord>()
                        .eq(RedeemRecord::getUserId, userId)
                        .orderByDesc(RedeemRecord::getCreateTime)
                        .last("LIMIT " + size + " OFFSET " + offset));
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return R.ok(data);
    }
}
