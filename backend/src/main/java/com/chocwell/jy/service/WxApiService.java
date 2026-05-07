package com.chocwell.jy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信服务端 API 封装
 *
 * 涉及两个接口：
 * 1. jscode2session  —— 用 loginCode 换 openid + session_key
 * 2. getuserphonenumber —— 用 phoneCode 换真实手机号（2023+ 新接口，无需解密）
 */
@Slf4j
@Service
public class WxApiService {

    // application.yml 中配置：app.wx.appid / app.wx.secret
    @Value("${app.wx.appid}")
    private String appid;

    @Value("${app.wx.secret}")
    private String secret;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WxApiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ─────────────────────────────────────────────────────────
    // 1. wx.login code → openid + session_key
    // ─────────────────────────────────────────────────────────
    public static class SessionResult {
        public String openid;
        public String sessionKey;
        public String unionid;  // 已绑定开放平台时才有
    }

    /**
     * 调用微信 jscode2session 接口
     * 文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html
     */
    public SessionResult code2Session(String loginCode) {
        String url = String.format(
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
            appid, secret, loginCode
        );

        String resp = restTemplate.getForObject(url, String.class);
        log.debug("jscode2session response: {}", resp);

        try {
            JsonNode node = objectMapper.readTree(resp);

            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                int errcode   = node.get("errcode").asInt();
                String errmsg = node.has("errmsg") ? node.get("errmsg").asText() : "unknown";
                log.error("jscode2session error: {} - {}", errcode, errmsg);
                throw new RuntimeException("微信登录失败：" + errmsg);
            }

            SessionResult result = new SessionResult();
            result.openid     = node.get("openid").asText();
            result.sessionKey = node.get("session_key").asText();
            if (node.has("unionid")) {
                result.unionid = node.get("unionid").asText();
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析微信响应失败", e);
        }
    }

    // ─────────────────────────────────────────────────────────
    // 2. getPhoneNumber code → 真实手机号
    // ─────────────────────────────────────────────────────────

    /**
     * 用 getPhoneNumber code 换真实手机号。
     * 需先拿到有效的 access_token（此处调 getAccessToken()）。
     * 文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-info/phone-number/getPhoneNumber.html
     */
    public String getPhoneNumber(String phoneCode) {
        String accessToken = getAccessToken();
        String url = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + accessToken;

        // 微信 API 严格只收 application/json + 标准 JSON 字符串。
        // 之前用 Map<String,String> + 显式 JSON header 仍然 412，
        // 推测 Spring 的 HttpMessageConverter 选择对 Map<String,String> 的处理路径不稳定。
        // 直接构造 JSON 字符串走 StringHttpMessageConverter，最稳。
        String jsonBody = "{\"code\":\"" + phoneCode + "\"}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        log.info("调微信 getuserphonenumber, body={}", jsonBody);
        String resp = restTemplate.postForObject(url, entity, String.class);
        log.info("getuserphonenumber response: {}", resp);

        try {
            JsonNode node = objectMapper.readTree(resp);

            int errcode = node.has("errcode") ? node.get("errcode").asInt() : 0;
            if (errcode != 0) {
                String errmsg = node.has("errmsg") ? node.get("errmsg").asText() : "unknown";
                log.error("getuserphonenumber error: {} - {}", errcode, errmsg);
                throw new RuntimeException("获取手机号失败：" + errmsg);
            }

            // phone_info.phoneNumber 是完整手机号（含国家区号）
            // phone_info.purePhoneNumber 是不含区号的纯手机号
            JsonNode phoneInfo = node.get("phone_info");
            return phoneInfo.get("purePhoneNumber").asText();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析手机号响应失败", e);
        }
    }

    // ─────────────────────────────────────────────────────────
    // 获取 access_token（生产建议加 Redis 缓存，有效期 2 小时）
    // ─────────────────────────────────────────────────────────

    /**
     * 获取小程序 access_token
     * ⚠️ 生产环境应将 token 缓存到 Redis（有效期 7200s），避免频繁请求
     */
    private String getAccessToken() {
        String url = String.format(
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
            appid, secret
        );

        String resp = restTemplate.getForObject(url, String.class);
        try {
            JsonNode node = objectMapper.readTree(resp);
            if (node.has("errcode")) {
                throw new RuntimeException("获取 access_token 失败：" + node.get("errmsg").asText());
            }
            return node.get("access_token").asText();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析 access_token 失败", e);
        }
    }
}
