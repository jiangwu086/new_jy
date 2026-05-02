package com.chocwell.jy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chocwell.jy.dto.LoginResponse;
import com.chocwell.jy.dto.PhoneLoginRequest;
import com.chocwell.jy.dto.SendSmsRequest;
import com.chocwell.jy.dto.WxLoginRequest;
import com.chocwell.jy.entity.SysUser;
import com.chocwell.jy.mapper.UserMapper;
import com.chocwell.jy.service.SmsService;
import com.chocwell.jy.service.WxApiService;
import com.chocwell.jy.util.JwtUtil;
import com.chocwell.jy.util.R;
import lombok.RequiredArgsConstructor;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 登录认证接口
 *
 * POST /api/v1/auth/wx-login      微信授权登录（真实手机号）
 * POST /api/v1/auth/send-sms      发送手机短信验证码
 * POST /api/v1/auth/phone-login   手机号 + 验证码登录
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final WxApiService wxApiService;
    private final SmsService   smsService;
    private final UserMapper   userMapper;
    private final JwtUtil      jwtUtil;

    // ================================================================
    // 1. 微信授权登录
    //    前端传来：
    //      loginCode  —— wx.login() 的 code，换 openid
    //      phoneCode  —— open-type="getPhoneNumber" 的 code，换手机号
    // ================================================================
    @PostMapping("/wx-login")
    public R<LoginResponse> wxLogin(@RequestBody WxLoginRequest req) {
        if (!StringUtils.hasText(req.getLoginCode())) {
            return R.fail("loginCode 不能为空");
        }

        try {
            // 1. 用 loginCode 换 openid + session_key（真实微信接口）
            WxApiService.SessionResult session = wxApiService.code2Session(req.getLoginCode());
            String openid = session.openid;

            // 2. 尝试用 phoneCode 换真实手机号；phoneCode 为空时（如开发者工具）跳过，手机号留空
            String phone = null;
            if (StringUtils.hasText(req.getPhoneCode())) {
                try {
                    phone = wxApiService.getPhoneNumber(req.getPhoneCode());
                } catch (Exception ex) {
                    log.warn("获取手机号失败，降级为仅 openid 登录: {}", ex.getMessage());
                }
            }

            // 3. 查找或创建用户（openid 为主键，phone 可为空）
            SysUser user = findOrCreateByOpenid(openid, phone, session.unionid);

            // 4. 生成 JWT
            String token = jwtUtil.generate(user.getId(), openid);

            log.info("微信登录成功: userId={}, openid={}, hasPhone={}", user.getId(), openid, phone != null);
            return R.ok(buildLoginResponse(token, user));

        } catch (Exception e) {
            log.error("微信登录失败: {}", e.getMessage(), e);
            return R.fail(e.getMessage());
        }
    }

    // ================================================================
    // 开发专用：模拟器快捷登录（不调任何微信接口）
    // POST /api/v1/auth/dev-login
    // body: { devKey }  — 简单防护，只允许固定 key
    // ================================================================
    @PostMapping("/dev-login")
    public R<LoginResponse> devLogin(@RequestBody Map<String, String> body) {
        if (!"DEV_MOCK_2026".equals(body.get("devKey"))) {
            return R.fail("非法请求");
        }
        // 固定的测试用户，openid 与 phone 唯一，保证每次登录同一账号
        String mockOpenid = "mock_devtools_user";
        String mockPhone  = "13800000000";

        SysUser user = userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getOpenid, mockOpenid)
        );
        if (user == null) {
            user = new SysUser();
            user.setOpenid(mockOpenid);
            user.setPhone(mockPhone);
            user.setNickName("模拟器测试用户");
            user.setTotalPoints(0);
            userMapper.insert(user);
        }
        String token = jwtUtil.generate(user.getId(), mockOpenid);
        log.info("模拟器登录: userId={}", user.getId());
        return R.ok(buildLoginResponse(token, user));
    }

    // ================================================================
    // 2. 发送短信验证码
    // ================================================================
    @PostMapping("/send-sms")
    public R<Void> sendSms(@RequestBody SendSmsRequest req) {
        String phone = req.getPhone();
        if (!isValidPhone(phone)) {
            return R.fail("手机号格式不正确");
        }

        try {
            // type 为空时默认走 register 模板（登录/注册场景）
            smsService.sendCode(phone, req.getType());
            return R.ok();
        } catch (Exception e) {
            log.error("短信发送失败: phone={}, type={}, error={}",
                      phone, req.getType(), e.getMessage(), e);
            return R.fail(e.getMessage());
        }
    }

    // ================================================================
    // 3. 手机号 + 验证码登录
    // ================================================================
    @PostMapping("/phone-login")
    public R<LoginResponse> phoneLogin(@RequestBody PhoneLoginRequest req) {
        String phone = req.getPhone();
        String code  = req.getCode();

        if (!isValidPhone(phone)) {
            return R.fail("手机号格式不正确");
        }
        if (!StringUtils.hasText(code) || code.length() != 6) {
            return R.fail("请输入6位验证码");
        }

        // 验证验证码（通过后从 Redis 中删除）
        if (!smsService.verifyCode(phone, code)) {
            return R.fail("验证码错误或已过期");
        }

        // 查找或创建用户（手机号注册，openid 可能为空）
        SysUser user = findOrCreateByPhone(phone);

        String token = jwtUtil.generate(user.getId(), user.getOpenid());

        log.info("手机号登录成功: userId={}, phone={}", user.getId(), phone);
        return R.ok(buildLoginResponse(token, user));
    }

    // ─── 私有工具方法 ──────────────────────────────────────────────

    /**
     * 根据 openid 查找用户，不存在则新建。
     * 若 openid 已有绑定用户但手机号为空，则补填手机号。
     */
    private SysUser findOrCreateByOpenid(String openid, String phone, String unionid) {
        SysUser user = userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getOpenid, openid)
        );

        if (user == null) {
            // 手机号可能已被其他登录方式注册，尝试合并账号
            user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone)
            );
            if (user != null) {
                // 已有手机号账号，补填 openid
                user.setOpenid(openid);
                if (StringUtils.hasText(unionid)) user.setUnionid(unionid);
                userMapper.updateById(user);
            } else {
                // 全新用户
                user = new SysUser();
                user.setOpenid(openid);
                if (StringUtils.hasText(unionid)) user.setUnionid(unionid);
                user.setPhone(phone);
                user.setTotalPoints(0);
                userMapper.insert(user);
            }
        } else {
            // 已有用户，更新手机号（如果为空）
            boolean needUpdate = false;
            if (!StringUtils.hasText(user.getPhone()) && StringUtils.hasText(phone)) {
                user.setPhone(phone);
                needUpdate = true;
            }
            if (!StringUtils.hasText(user.getUnionid()) && StringUtils.hasText(unionid)) {
                user.setUnionid(unionid);
                needUpdate = true;
            }
            if (needUpdate) userMapper.updateById(user);
        }
        return user;
    }

    /**
     * 根据手机号查找用户，不存在则新建。
     */
    private SysUser findOrCreateByPhone(String phone) {
        SysUser user = userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone)
        );
        if (user == null) {
            user = new SysUser();
            user.setPhone(phone);
            user.setTotalPoints(0);
            userMapper.insert(user);
        }
        return user;
    }

    private LoginResponse buildLoginResponse(String token, SysUser user) {
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .phone(user.getPhone())
                .openid(user.getOpenid())
                .nickName(user.getNickName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    private boolean isValidPhone(String phone) {
        return StringUtils.hasText(phone) && phone.matches("^1[3-9]\\d{9}$");
    }
}
