package com.chocwell.jy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 微信接口封装：access_token 管理 + 小程序码生成 + 订阅消息
 */
@Service
@RequiredArgsConstructor
public class WxService {

    @Value("${app.wx.appid}")
    private String appid;

    @Value("${app.wx.secret}")
    private String secret;

    // 订阅消息模板 ID（在微信公众平台申请后填入 application.yml）
    @Value("${app.wx.tmpl-checkin:}")
    private String tmplCheckin;

    @Value("${app.wx.tmpl-gift:}")
    private String tmplGift;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ── access_token 缓存（有效期 7200s，提前 60s 刷新）─────────
    private final AtomicReference<String> cachedToken = new AtomicReference<>("");
    private final AtomicLong tokenExpireAt = new AtomicLong(0);

    public String getAccessToken() {
        if (System.currentTimeMillis() < tokenExpireAt.get()) {
            return cachedToken.get();
        }
        String url = "https://api.weixin.qq.com/cgi-bin/token"
                + "?grant_type=client_credential&appid=" + appid + "&secret=" + secret;
        String resp = restTemplate.getForObject(url, String.class);
        try {
            JsonNode node = objectMapper.readTree(resp);
            String token = node.path("access_token").asText();
            long expiresIn = node.path("expires_in").asLong(7200);
            cachedToken.set(token);
            tokenExpireAt.set(System.currentTimeMillis() + (expiresIn - 60) * 1000);
            return token;
        } catch (Exception e) {
            throw new RuntimeException("获取微信 access_token 失败: " + resp, e);
        }
    }

    // ── 生成小程序码（无限制码，scene 最长 32 字节）────────────
    public byte[] getUnlimitedQrCode(String scene, String page) {
        String token = getAccessToken();
        String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + token;

        Map<String, Object> body = new HashMap<>();
        body.put("scene", scene);
        body.put("page",  page != null ? page : "pages/checkin/form");
        body.put("width", 280);
        body.put("auto_color", false);
        body.put("line_color", Map.of("r", 59, "g", 130, "b", 246)); // #3b82f6

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<byte[]> resp = restTemplate.exchange(url, HttpMethod.POST, req, byte[].class);
        byte[] bytes = resp.getBody();
        // 如果微信返回 JSON（出错），body 开头是 '{'
        if (bytes != null && bytes.length > 0 && bytes[0] == '{') {
            throw new RuntimeException("获取小程序码失败: " + new String(bytes));
        }
        return bytes;
    }

    // ── 发送订阅消息（底层）────────────────────────────────────
    public void sendSubscribeMessage(String openid, String templateId, String page,
                                     Map<String, Map<String, String>> data) {
        if (openid == null || openid.isBlank() || templateId == null || templateId.isBlank()) return;
        String token = getAccessToken();
        String url   = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + token;

        Map<String, Object> body = new HashMap<>();
        body.put("touser",      openid);
        body.put("template_id", templateId);
        body.put("page",        page);
        body.put("data",        data);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            // 订阅消息发送失败不影响主流程
        }
    }

    // ── 业务封装：打卡成功通知 ──────────────────────────────────
    /**
     * 发送打卡成功订阅消息（模板：签到提醒）
     *   time1        → 签到时间
     *   short_thing2 → 签到积分
     *   thing12      → 设备位置（打卡点名称）
     */
    public void notifyCheckin(String openid, String locationName, int points, String checkinTime) {
        if (tmplCheckin == null || tmplCheckin.isBlank()) return;
        Map<String, Map<String, String>> data = new HashMap<>();
        data.put("time1",        Map.of("value", checkinTime));
        data.put("short_thing2", Map.of("value", shorten(String.valueOf(points) + " 积分", 10)));
        data.put("thing12",      Map.of("value", shorten(locationName, 20)));
        sendSubscribeMessage(openid, tmplCheckin, "pages/record/record", data);
    }

    // ── 业务封装：礼品兑换成功通知 ──────────────────────────────
    /**
     * 发送礼品兑换成功订阅消息（模板：兑奖通知）
     *   thing5 → 奖品名称
     *   thing2 → 审核结果（固定填"兑换成功"）
     *   time3  → 审核时间（兑换时间）
     */
    public void notifyGiftExchange(String openid, String giftName, int cost, String exchangeTime) {
        if (tmplGift == null || tmplGift.isBlank()) return;
        Map<String, Map<String, String>> data = new HashMap<>();
        data.put("thing5", Map.of("value", shorten(giftName, 20)));
        data.put("thing2", Map.of("value", "兑换成功，消耗" + cost + "积分"));
        data.put("time3",  Map.of("value", exchangeTime));
        sendSubscribeMessage(openid, tmplGift, "pages/gift/index", data);
    }

    /** 截断字符串至 maxLen 字符（微信 thing 类型字段最长 20 字）*/
    private String shorten(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }
}
