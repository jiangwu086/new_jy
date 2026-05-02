package com.chocwell.jy.controller;

import com.chocwell.jy.interceptor.UserAuthInterceptor;
import com.chocwell.jy.service.GiftService;
import com.chocwell.jy.util.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 小程序端礼品接口
 * GET  /api/v1/gifts       — 公开，返回可用礼品列表
 * POST /api/v1/gifts/{id}/exchange — 需登录，在线兑换
 */
@RestController
@RequestMapping("/api/v1/gifts")
@RequiredArgsConstructor
public class GiftController {

    private final GiftService giftService;

    /**
     * 获取礼品列表（公开）
     * orgId 可选：传入则同时返回该机构专属礼品
     */
    @GetMapping
    public R<?> list(@RequestParam(required = false) Long orgId) {
        return R.ok(giftService.listForUser(orgId));
    }

    /**
     * 在线兑换礼品（需登录）
     * POST /api/v1/gifts/{id}/exchange
     */
    @PostMapping("/{id}/exchange")
    public R<?> exchange(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(UserAuthInterceptor.USER_ID_KEY);
        if (userId == null) return R.fail("请先登录");
        try {
            return R.ok(giftService.exchange(userId, id));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return R.fail(e.getMessage());
        }
    }
}
